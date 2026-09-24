package claude.multiplan

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Success, Failure}

/** Where this toolchain keeps its own files. Overridable so the binaries stay
  * testable and relocatable without a rebuild.
  */
object Root:
  def home: Path = Paths.get(sys.props("user.home"))

  def expand(s: String): Path =
    if s == "~" then home
    else if s.startsWith("~/") then home.resolve(s.drop(2))
    else Paths.get(s)

  /** ~/asd/claude by default; CLAUDE_MULTIPLAN_ROOT wins when set. */
  def dir: Path =
    sys.env.get("CLAUDE_MULTIPLAN_ROOT").map(expand).getOrElse(home.resolve("asd/claude"))

  def registry: Path = dir.resolve("config/profiles.json")

/** One account: its own configuration home plus the settings overlays that
  * describe how this plan differs from the shared baseline.
  *
  * `configDir` is empty for the profile that keeps Claude Code's stock layout.
  * That case is not cosmetic: with CLAUDE_CONFIG_DIR unset the global config
  * file sits at ~/.claude.json, but setting CLAUDE_CONFIG_DIR=~/.claude would
  * make Claude Code look for ~/.claude/.claude.json instead — a different,
  * empty file, orphaning an existing sign-in. So the stock profile is launched
  * with the variable left alone.
  */
final case class Profile(
    name: String,
    configDir: Option[Path],
    overlays: List[Path],
    description: String
):
  def isStock: Boolean = configDir.isEmpty
  def configHome: Path = configDir.getOrElse(Root.home.resolve(".claude"))
  def globalJson: Path =
    configDir.fold(Root.home.resolve(".claude.json"))(_.resolve(".claude.json"))

final case class Registry(shared: List[Path], profiles: List[Profile]):
  def find(name: String): Either[String, Profile] =
    profiles.find(_.name == name).toRight:
      s"unknown profile '$name'; known: ${profiles.map(_.name).mkString(", ")}"

object Registry:
  def load(file: Path = Root.registry): Either[String, Registry] =
    if !Files.exists(file) then Left(s"no profile registry at $file")
    else
      Try(ujson.read(Files.readString(file))) match
        case Failure(e) => Left(s"$file is not valid JSON: ${e.getMessage}")
        case Success(js) =>
          Try {
            def paths(v: ujson.Value): List[Path] =
              v.arr.toList.map(p => Root.dir.resolve(Root.expand(p.str)))

            val shared = js.obj.get("shared").map(paths).getOrElse(Nil)
            val profiles = js("profiles").obj.toList.map: (name, spec) =>
              Profile(
                name = name,
                configDir = spec.obj.get("configDir")
                  .filterNot(_.isNull)
                  .map(v => Root.expand(v.str)),
                overlays = spec.obj.get("overlays").map(paths).getOrElse(Nil),
                description = spec.obj.get("description").map(_.str).getOrElse("")
              )
            Registry(shared, profiles.sortBy(_.name))
          }.toEither.left.map(e => s"$file has the wrong shape: ${e.getMessage}")

/** Deep merge that mirrors how Claude Code combines settings across scopes:
  * a later source wins for scalars and merges objects key by key, while list
  * keys accumulate instead of replacing one another.
  */
object Merge:
  def deep(base: ujson.Value, overlay: ujson.Value): ujson.Value =
    (base, overlay) match
      case (b: ujson.Obj, o: ujson.Obj) =>
        val merged = scala.collection.mutable.LinkedHashMap.from(b.value)
        for (k, v) <- o.value do
          merged.update(k, merged.get(k).map(deep(_, v)).getOrElse(v))
        ujson.Obj.from(merged)
      case (b: ujson.Arr, o: ujson.Arr) =>
        ujson.Arr.from((b.value ++ o.value).distinct)
      case _ => overlay

  def all(files: List[Path]): Either[String, ujson.Value] =
    files.foldLeft[Either[String, ujson.Value]](Right(ujson.Obj())): (acc, f) =>
      for
        soFar <- acc
        text <- Try(Files.readString(f)).toEither.left.map(_ => s"cannot read $f")
        js <- Try(ujson.read(text)).toEither.left.map(e => s"$f is not valid JSON: ${e.getMessage}")
      yield deep(soFar, js)

object Launcher:
  /** The merged file is written inside the profile's own configuration home so
    * it is easy to inspect, and so a stale one can never leak to another plan.
    * Claude Code does not read this name on its own; it is passed explicitly.
    */
  def effectiveSettings(p: Profile, registry: Registry): Either[String, Path] =
    for merged <- Merge.all(registry.shared ++ p.overlays)
    yield
      Files.createDirectories(p.configHome)
      val out = p.configHome.resolve("effective-settings.json")
      Files.writeString(out, ujson.write(merged, indent = 2) + "\n")
      out

  def claudeBinary: String = sys.env.getOrElse("CLAUDE_BIN", "claude")

  def run(p: Profile, settings: Path, args: List[String]): Int =
    val cmd = (claudeBinary :: "--settings" :: settings.toString :: args).asJava
    val pb = new ProcessBuilder(cmd).inheritIO()
    p.configDir.foreach: d =>
      pb.environment().put("CLAUDE_CONFIG_DIR", d.toAbsolutePath.toString)
    // Never let an inherited value of these decide the model for a plan that
    // cannot serve it; the overlay's `model` key is the single source of truth.
    pb.environment().remove("ANTHROPIC_MODEL")
    pb.environment().remove("ANTHROPIC_DEFAULT_MODEL")
    Try(pb.start().waitFor()) match
      case Success(code) => code
      case Failure(e) =>
        Console.err.println(s"could not start ${claudeBinary}: ${e.getMessage}")
        127

/** Reads the account block Claude Code maintains in <configDir>/.claude.json. */
final case class Account(
    email: String, org: String, orgType: String,
    role: String, seatTier: String, rateLimitTier: String, billing: String
):
  /** The plan is not in any single field. A Team seat identifies itself with
    * `seatTier`, but a Max account leaves `seatTier` null and names its tier in
    * `organizationRateLimitTier` (`default_claude_max_5x`). Read both.
    */
  def plan: String = (orgType, seatTier) match
    case ("claude_max", _) =>
      if rateLimitTier.contains("max_20x") then "Max 20x"
      else if rateLimitTier.contains("max_5x") then "Max 5x"
      else "Max"
    case ("claude_team", "team_labs_standard") => "Team for Scientists — standard seat"
    case ("claude_team", "team_labs_premium")  => "Team for Scientists — premium seat"
    case ("claude_team", t) if t.startsWith("team_") => s"Team — $t"
    case ("claude_pro", _) => "Pro"
    case _ =>
      if seatTier == "-" then orgType else s"$orgType / $seatTier"

  /** Fable needs pay-as-you-go credits on Pro and standard Team seats; it is
    * included on Max and on premium seats. See claude-profile-draft.md §2.
    */
  def fableIncluded: Boolean =
    orgType == "claude_max" || seatTier.endsWith("_premium")

object Account:
  def of(p: Profile): Either[String, Account] =
    val f = p.globalJson
    if !Files.exists(f) then Left("not signed in yet (no .claude.json)")
    else
      Try {
        val o = ujson.read(Files.readString(f))("oauthAccount")
        def s(k: String) = o.obj.get(k).flatMap(v => Try(v.str).toOption).getOrElse("-")
        Account(s("emailAddress"), s("organizationName"), s("organizationType"),
          s("organizationRole"), s("seatTier"), s("organizationRateLimitTier"),
          s("billingType"))
      }.toEither.left.map(_ => "signed out, or no account block in .claude.json")
