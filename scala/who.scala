package agaton

/** `agaton who` — which account each profile is signed into, and on what plan.
  *
  * Kept separate from the CLI dispatch because it is the one read-only report
  * that a wrapper or a statusline might want to call on its own.
  */
object Who:

  /** Returns Left with a message the caller can die on, so this stays free of
    * its own exit handling. */
  def report(registry: Registry, only: Option[String]): Either[String, Unit] =
    val wanted = only match
      case None       => Right(registry.profiles)
      case Some(name) => registry.find(name).map(List(_))

    wanted.map: profiles =>
      for p <- profiles do
        val tag = if p.isStock then " (stock layout)" else ""
        println(s"[${p.name}]  ${p.home}$tag")
        Account.of(p) match
          case Left(err) => println(s"  $err")
          case Right(a) =>
            println(s"  account   ${a.email}")
            println(s"  plan      ${a.plan}")
            println(s"  provider  ${p.provider}")
            println(s"  org       ${a.org} — ${a.orgType}, you are '${a.role}'")
            println(s"  tiers     seat=${a.seatTier}  rateLimit=${a.rateLimitTier}")
            println(s"  fable     ${if a.fableIncluded then "included in plan"
                                    else "needs usage credits (not included)"}")
            println(s"  billing   ${a.billing}")
        println()
