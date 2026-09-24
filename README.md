# agaton

Run several coding-agent accounts from one Unix user — side by side, in separate terminals,
without orphaning a sign-in.

> **Status: early.** Claude Code only, and nothing released yet — no binary to install, no tests.
> The CLI below does run: `list`, `show`, `who` and `doctor` work against a real two-account setup,
> and `run` launches. See [Roadmap](#roadmap) for what is missing.

## Why

Holding two accounts for the same tool is increasingly ordinary — an employer seat and a privately
paid subscription, or one account per client. The tools assume one.

The stock advice for Claude Code is "set `CLAUDE_CONFIG_DIR`", which is necessary and not
sufficient. Point it at `$HOME/.claude` and Claude Code looks for `$HOME/.claude/.claude.json`
instead of `$HOME/.claude.json` — a different, empty file — silently orphaning the sign-in you were
trying to protect. The failure is quiet: you get a login prompt, not an error.

So the isolation has to be deliberate, and the settings you want *shared* between accounts have to
be merged back in on purpose. That is what agaton is for.

## Install

There is no release binary yet, so this builds from source and needs
[scala-cli](https://scala-cli.virtuslab.org/install). A native build also needs a C toolchain and
zlib headers (Debian/Ubuntu: `build-essential zlib1g-dev`); `--jvm` avoids both.

```
git clone https://github.com/bjornregnell/agaton.git && cd agaton
scala-cli run get-agaton.sc -- --dry-run     # read it first, then see what it would do
scala-cli run get-agaton.sc                  # build, install to ~/.local/bin, seed the config
```

It installs one binary and seeds `$AGATON_HOME` with starter profiles **only if no registry is
there already** — it never overwrites your profiles.json, and never uses sudo. `--prefix` and
`--home` move either destination.

The installer is a Scala script rather than a shell script because building already requires
scala-cli, so it costs no extra dependency and works on Windows too.

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
agaton run work -- --continue   launch the provider for profile `work`, passing args through
agaton work                     shorthand for the same
agaton who                      which account each profile holds, and its plan
agaton list                     the profiles that exist
agaton show work                the settings that profile would actually apply
agaton doctor                   check the setup is intact before it fails at you
```

`run` rather than `use`: this launches one process under a chosen identity. It does not switch a
persistent global mode the way `kubectl use-context` does. Subcommand names are reserved and
refused as profile names, so the shorthand can never shadow a command.

Profiles live in `$AGATON_HOME/profiles.json`, defaulting to
`${XDG_CONFIG_HOME:-~/.config}/agaton`. Point `AGATON_HOME` at a synced folder if you want the same
profiles on several machines.

Named for [Agaton Sax](https://en.wikipedia.org/wiki/Agaton_Sax), who got into places by assuming
another identity.

## How this differs from Claude Code Projects

Projects (beta) parallelises work **within one account**, so it overlaps with one reason people run
several logins — wanting more sessions at once. It does not address the others, and those are
agaton's niche:

| | Projects | agaton |
|---|---|---|
| more parallel work | yes, within one account | yes, by using several accounts |
| **separate identities** | no — one signed-in account | each profile has its own configuration home and sign-in |
| **separate billing** | no — one plan's limits and invoice | an employer seat and a private subscription stay apart |
| **separate providers** | Claude Code only | the model allows Claude, Codex, opencode (only Claude implemented) |

So they are complementary rather than competing: if your only problem is "I want more work in
flight on my own account", use Projects. If the accounts must stay distinct — because one is billed
to an employer, carries org policy, or must never see the other's material — that is a boundary
Projects does not draw.

## Roadmap

- [x] Import the working Claude Code prototype (a profile registry, a deep-merged settings layer,
      and a GraalVM native binary) from its previous home outside this repository.
- [x] Terminology pass — the four nouns above in the code, then the verbs.
- [ ] Extract a short getting-started from the handbook, once the CLI stops moving.
- [ ] Tests, and a release binary worth installing.
- [ ] Second provider, once there is a real one to test against. Designing the abstraction before
      running Codex or opencode would produce the wrong seams.

## License

Apache-2.0. Copyright 2026 bjornregnell.
