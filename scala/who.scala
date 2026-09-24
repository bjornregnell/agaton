package claude.multiplan

/** claude-who — which account is each profile signed into, and on what plan. */
object WhoMain:

  def main(args: Array[String]): Unit =
    val registry = Registry.load().fold(
      err => { Console.err.println(s"claude-who: $err"); sys.exit(2) },
      identity
    )
    val only = args.headOption
    val wanted = only.fold(registry.profiles)(n => registry.find(n).fold(
      err => { Console.err.println(s"claude-who: $err"); sys.exit(2) },
      List(_)
    ))

    for p <- wanted do
      val tag = if p.isStock then " (stock layout)" else ""
      println(s"[${p.name}]  ${p.configHome}$tag")
      Account.of(p) match
        case Left(err) => println(s"  $err")
        case Right(a) =>
          println(s"  account   ${a.email}")
          println(s"  plan      ${a.plan}")
          println(s"  org       ${a.org} — ${a.orgType}, you are '${a.role}'")
          println(s"  tiers     seat=${a.seatTier}  rateLimit=${a.rateLimitTier}")
          println(s"  fable     ${if a.fableIncluded then "included in plan"
                                  else "needs usage credits (not included)"}")
          println(s"  billing   ${a.billing}")
      println()
