package claude.multiplan

import java.nio.file.Files

/** claude-profile — start Claude Code under one account's configuration home.
  *
  *   claude-profile work                 # start the work account
  *   claude-profile personal -- -p "hi"  # pass arguments through to claude
  *   claude-profile list                 # profiles and where they live
  *   claude-profile show personal        # the settings that will be applied
  *   claude-profile doctor               # check the setup
  */
object LauncherMain:

  private def usage = """usage: claude-profile <profile> [-- <claude args>...]
       claude-profile list | show <profile> | doctor

profiles come from ~/asd/claude/config/profiles.json
(override the root with CLAUDE_MULTIPLAN_ROOT)"""

  private def die(msg: String): Nothing =
    Console.err.println(s"claude-profile: $msg")
    sys.exit(2)

  def main(args: Array[String]): Unit =
    val registry = Registry.load().fold(die, identity)

    args.toList match
      case Nil | ("-h" | "--help") :: _ =>
        println(usage)

      case "list" :: _ =>
        val width = registry.profiles.map(_.name.length).maxOption.getOrElse(0)
        for p <- registry.profiles do
          val signedIn = Account.of(p).fold(err => s"($err)", a => s"${a.email} — ${a.org}")
          val home =
            if p.isStock then s"${p.configHome}  (stock layout, CLAUDE_CONFIG_DIR unset)"
            else p.configHome.toString
          println(s"${p.name.padTo(width, ' ')}  $home")
          println(s"${" " * width}  $signedIn")
          if p.description.nonEmpty then println(s"${" " * width}  ${p.description}")

      case "show" :: name :: _ =>
        val p = registry.find(name).fold(die, identity)
        val merged = Merge.all(registry.shared ++ p.overlays).fold(die, identity)
        println(ujson.write(merged, indent = 2))

      case "doctor" :: _ =>
        var problems = 0
        def check(ok: Boolean, label: String, detail: String = ""): Unit =
          if !ok then problems += 1
          val hint = if ok || detail.isEmpty then "" else s" — $detail"
          println(s"${if ok then "ok  " else "FAIL"}  $label$hint")

        val onPath = Runtime.getRuntime.exec(Array("which", Launcher.claudeBinary)).waitFor() == 0
        check(onPath, s"${Launcher.claudeBinary} on PATH")
        for p <- registry.profiles do
          check(Files.isDirectory(p.configHome), s"${p.name}: config home ${p.configHome}",
            "create it, or run the profile once")
          check(Files.exists(p.configHome.resolve(".credentials.json")),
            s"${p.name}: signed in", "run it once and use /login")
          check(Merge.all(registry.shared ++ p.overlays).isRight, s"${p.name}: settings parse")
        sys.exit(if problems == 0 then 0 else 1)

      case name :: rest =>
        val p = registry.find(name).fold(die, identity)
        val settings = Launcher.effectiveSettings(p, registry).fold(die, identity)
        val passthrough = rest match
          case "--" :: tail => tail
          case other        => other
        sys.exit(Launcher.run(p, settings, passthrough))
