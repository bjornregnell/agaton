# agaton

Run several coding-agent accounts from one Unix user — side by side, in separate terminals,
without orphaning a sign-in.

> **Status: early.** Claude Code only, nothing released, the CLI below is a design sketch rather
> than a description of working software. A functioning prototype exists outside this repository
> and is being migrated in; see [Roadmap](#roadmap).

## Why

Holding two accounts for the same tool is increasingly ordinary — an employer seat and a privately
paid subscription, or one account per client. The tools assume one.

The stock advice for Claude Code is "set `CLAUDE_CONFIG_DIR`", which is necessary and not
sufficient. Point it at `$HOME/.claude` and Claude Code looks for `$HOME/.claude/.claude.json`
instead of `$HOME/.claude.json` — a different, empty file — silently orphaning the sign-in you were
trying to protect. The failure is quiet: you get a login prompt, not an error.

So the isolation has to be deliberate, and the settings you want *shared* between accounts have to
be merged back in on purpose. That is what agaton is for.

## The model

Four nouns, kept separate because conflating them is what makes existing tooling confusing:

| noun | is |
|---|---|
| **provider** | the tool being launched — Claude Code, Codex, opencode |
| **account** | the paying identity — an employer seat, a private subscription |
| **profile** | the named bundle: provider + account + settings overlay |
| **home** | the isolated configuration directory (`CLAUDE_CONFIG_DIR`, `CODEX_HOME`, …) |

Today "profile" tends to mean the account and the directory at once. Splitting them is what lets a
second provider be added without redefining everything.

## Planned CLI

```
agaton as work -- --continue    launch the provider for profile `work`, passing args through
agaton who                      which account each profile holds, and its plan
agaton list                     the profiles that exist
agaton show work                the settings that profile would actually apply
agaton doctor                   check the setup is intact before it fails at you
```

`as` rather than `use`: this launches one process under a chosen identity, like `sudo -u`. It does
not switch a persistent global mode the way `kubectl use-context` does.

Named for [Agaton Sax](https://en.wikipedia.org/wiki/Agaton_Sax), who got into places by assuming
another identity.

## Roadmap

- [ ] Import the working Claude Code prototype (a profile registry, a deep-merged settings layer,
      and two GraalVM native binaries) from its current home outside this repository.
- [ ] Terminology pass — settle the four nouns above in the code, then the verbs.
- [ ] Second provider, once there is a real one to test against. Designing the abstraction before
      running Codex or opencode would produce the wrong seams.

## License

Apache-2.0. Copyright 2026 bjornregnell.
