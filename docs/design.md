# Running two Claude plans from one Linux user

Notes for `work@university.example` (work) + a private Max 5× account, on Ubuntu,
Claude Code 2.1.272.

Everything marked **verified** below was checked against this machine or the
official docs on 2026-09-15. Everything marked **unverified** was not.

---

## 0. Start here

**What this is.** The author runs two Claude accounts on one Ubuntu user: a
work seat in the the university org org (Claude Team plan for scientists) and a privately
paid Max 5x. This directory holds a small Scala toolchain that lets both run at
once, in separate terminals, sharing one settings baseline while differing where
the plans differ. This file is both the design record and the onboarding doc.

**Layout.**

```
~/asd/claude/
├── claude-profile-draft.md  this file — authoritative; the Scala blocks in §5
│                            are GENERATED from scala/, never hand-edit them
├── EMBER.md                 volatile handover state; delete when settled
├── config/                  profiles.json (registry) + shared/work/personal
├── scala/                   sources; one directory, four main classes
├── filing/                  issues prepared for genscalator (+ superseded/)
├── tmp/                     scratch tools (anchored-edit, bash-mix) + patches
└── bin/                     native binaries, symlinked into ~/.local/bin
```

**Current state.** Both profiles are set up, signed in and verified working
(§5, "Verified working, both plans live"). `claude-profile doctor` exits 0.

**Orient yourself in 10 seconds.**

```
claude-who                 # which account and plan each profile holds
claude-profile doctor      # 7 checks; exit 0 means the setup is intact
claude-profile show work   # the settings that profile will actually apply
```

**If you are here to change something, read §6 first.** It lists the invariants
— five things that look harmless to "simplify" and each of which silently breaks
a profile — plus the edit/verify loop. §9 is the accumulated gotcha list.

**Plugins:** user scope is per profile, project and local scope live in the
repository and cross profiles. §3 has the full table.

**Handover in progress?** If `EMBER.md` exists beside this file, read it — it
holds the volatile state this file deliberately does not: what is in flight,
what is blocked on a human, and which facts have expired. It is written to be
deleted once its work is done. This file stays authoritative for everything
else.

**What is settled and should not be re-investigated:** the work account is on
the scientists plan (confirmed by the org owner, not machine-readable — §1); the
personal account is Max 5x (`organizationRateLimitTier: default_claude_max_5x`);
Fable is included on Max and needs credits on the work seat (§2).

---

## 1. What the two accounts are

### Work — verified from `~/.claude.json` and `~/.claude/.credentials.json`

| field | value |
|---|---|
| account | `work@university.example` |
| organization | **the university org** (`organizationType: claude_team`) |
| your role | `user` (not an org admin) |
| seat tier | `team_labs_standard` |
| billing | `stripe_subscription`, subscription created 2026-09-02 |
| extra usage | **off**, `cachedExtraUsageDisabledReason: org_level_disabled` |

This is the **Claude Team plan for scientists**. Confirmed 2026-09-15 by the
org's primary owner — the Principal Investigator (PI) who holds the grant the
seats sit under — who states that this account holds one of their
scientific-plan seats under the work email. That is an out-of-band human
confirmation, not something readable from any local file or from the claude.ai
UI at `user` role — so treat it as settled, but do not expect a future session to
be able to re-derive it from the machine.

The program is the one Anthropic opened in August 2026, offering free standard
seats and $15/mo premium seats to PIs at academic and non-profit research
institutions — the PI plus up to 25 lab members, price fixed for a year. The
local evidence that pointed
here before the PI confirmed it: the `_labs_` seat tier, an academic org, a
subscription created days after the program launched, and extra usage disabled
at the org level rather than by you.

**Corroborated 2026-09-15** from claude.ai → Settings → General on the work
account: the Organization ID shown there was byte-identical to the local
`oauthAccount.organizationUuid` — role **User**, primary owner the **PI**, and
*"Please contact your administrator to deprovision your account"*. (The actual
UUID is deliberately not reproduced here; the point is that the two agree, and
the check is one you run on your own machine.)

**Note for a future session:** that page confirms *which* org and *what role*,
but names no plan, seat type or billing, and a `user` role cannot see those
anywhere in the claude.ai UI. So the plan is **not** machine-verifiable from this
account. If you find yourself re-investigating it, stop: it was settled by
asking the org owner, and that answer is recorded above.

### The upgrade path, if you want Fable on the work seat

The scientists program has two seat types: free standard seats, and premium
seats at $15/mo with 5x the usage limits and Fable included. You are on
`team_labs_standard`. You cannot change that yourself — you are `user`, and
extra usage is blocked at org level — but the primary owner can assign a premium
seat. That, not usage credits, is the realistic route to Fable on the work
account.

### Personal — verified from `~/.claude-personal/.claude.json`

| field | value |
|---|---|
| account | `personal@example.com` |
| organization | *"…'s Organization"* (`organizationType: claude_max`) |
| your role | `admin` — it is your own one-person org |
| seat tier | **`null`** — Max accounts do not use this field |
| rate-limit tier | `default_claude_max_5x` ← **this is where "5x" is stated** |
| billing | `stripe_subscription`, subscription created 2026-06-29 |

### How the plan is actually identified

This was the open question, and the answer is that **no single field holds the
plan**. The two accounts state it in different places:

| | work | personal |
|---|---|---|
| `organizationType` | `claude_team` | `claude_max` |
| `seatTier` | `team_labs_standard` | `null` |
| `organizationRateLimitTier` | `default_raven` | `default_claude_max_5x` |
| credentials `subscriptionType` | `team` | `max` |

So a Team seat identifies itself through `seatTier`, while a Max account leaves
that null and names its tier in `organizationRateLimitTier`. Any tool that reads
only `seatTier` — as the first version of `claude-who` did — will report a Max
account as having no plan at all. `Account.plan` in §5 reads both.

One asymmetry worth knowing: on the personal account you are `admin` of your own
organization, so enabling extra usage is yours to decide. On the university org you are
`user` and it is disabled at the org level, which is what puts Fable out of
reach there.

---

## 2. Which models each plan gives you

Model availability is **per plan**, and this is the main reason the two profiles
want different settings.

| | work: Team standard seat | personal: Max 5× (confirmed) |
|---|---|---|
| Opus 5 | yes | yes — Opus 5 is the Max default |
| Opus with 1M context | **yes, in practice** | yes, included |
| Sonnet 5 | yes | yes |
| Fable 5 / 5.1 | **no, effectively** | **yes, included** |
| Haiku 4.5 | yes | yes |

**Fable is the real difference.** Per Anthropic's help centre, on *Max plans,
premium Team seats and premium Enterprise seats* Fable 5 and 5.1 are included as
standard and you may spend up to **50% of your weekly usage limits** on them at
no extra cost. On *Pro plans and standard Team seats* — which is your work seat —
Fable runs on **pay-as-you-go usage credits** and is not part of the plan's
standard limits. And your org has extra usage **disabled at the org level**, which
you cannot change as a `user`. So: Fable on personal, not on work.

This makes the `best` alias plan-dependent. `best` resolves to *Fable where
available, otherwise Opus* — a sensible default on personal, a surprise on work.
Pin the model per profile rather than relying on `best`.

**1M context.** The docs say Max, Team Premium and Enterprise get Opus extended
to 1M, while *Pro and Team Standard* need usage credits for it. Yet this machine
is running `claude-opus-5[1m]` on the work standard seat right now, and at least
one secondary source states Team Standard seats are auto-upgraded too. Treat the
work side as **working but not doc-guaranteed** — if `opus[1m]` ever starts
erroring on work, fall back to `opus`. On Max it is documented and included.

Model aliases accepted by `model` / `--model` / `/model`:
`default`, `best`, `fable`, `sonnet`, `opus`, `haiku`, `sonnet[1m]`, `opus[1m]`,
`opusplan`, or a full id such as `claude-opus-5`, `claude-fable-5-1`.

---

## 3. How the isolation works

`CLAUDE_CONFIG_DIR` relocates Claude Code's entire **configuration home**:
settings, credentials, session history, projects, plugins, memory.

**Verified on this machine:** with `CLAUDE_CONFIG_DIR` pointed at an empty
directory, `claude mcp list` created `<that dir>/.claude.json` and left
`~/.claude.json` untouched. Two terminals with different values are two fully
separate Claude Codes, running at the same time.

Two constraints, both enforced by the CLI itself:

- **It must be an absolute path.** The binary carries the error string
  *"the configuration home (CLAUDE_CONFIG_DIR) is not an absolute path"*.
- **It must be set in the shell, not in a settings file.** The binary carries
  *"…changed after Claude Code started: set it in the shell, not a settings file"*.
  A launcher process that sets it before `exec` is exactly the supported shape.

### What actually lives in a configuration home

Everything below sits inside the configuration home and therefore moves with
`CLAUDE_CONFIG_DIR`. This is the inventory observed in `~/.claude` on this
machine:

| path | what it is |
|---|---|
| `.credentials.json` | OAuth access + refresh token for **this** account |
| `settings.json` | your user-level settings (precedence level 5) |
| `projects/` | per-project transcripts (`.jsonl`) and agent memory |
| `sessions/`, `session-env/` | live session state |
| `history.jsonl` | prompts you have typed |
| `file-history/` | edit history, backing rewind |
| `shell-snapshots/` | captured shell environments |
| `plugins/` | marketplaces, `installed_plugins.json`, and the plugin cache — **per profile** |
| `cache/`, `downloads/`, `paste-cache/` | asset caches |
| `policy-limits.json` | org policy fetched for this account |
| `backups/`, `debug/` | config snapshots and debug logs |
| `statsig/` (if present) | feature-flag state |

Plus, one level up in the stock layout, `~/.claude.json` — the global config
file holding the sign-in session, MCP server definitions, per-project trust
decisions and the global keys `/config` writes. This is the file whose location
shifts, and the reason for the trap below.

Two of these deserve attention:

- **`.credentials.json` is plaintext on Linux.** The CLI carries the string
  *"Warning: Storing credentials in plaintext."*; the keychain path in the binary
  is macOS-only. The file is mode `600`. Two profiles means two plaintext token
  files, so anything that can read your home directory can act as *both*
  accounts. Never copy this file between profiles, never commit a config home,
  and if you back one up, encrypt it.
- **`policy-limits.json` is per account.** Work carries
  `allow_remote_control: false` and `allow_quick_web_setup: false` from the university org
  policy. The personal profile fetches its own and will not have them. That is
  why a feature can exist in one window and not the other.

### How plugins are handled across the two profiles

Plugin state lives in `<configHome>/plugins/`, so it moves with
`CLAUDE_CONFIG_DIR` — but only for some scopes. Claude Code has four, and **two
of them live in the repository instead of the config home**, which is how a
plugin crosses profiles.

| scope | what it means | where it is stored | crosses profiles? |
|---|---|---|---|
| **user** | you, in every project | `<configHome>/plugins/installed_plugins.json` | **no** — per profile |
| **project** | everyone who clones this repo | the repo's `.claude/settings.json` (`enabledPlugins`) | **yes** — whichever profile opens the repo |
| **local** | you, in this repo only | the repo's `.claude/settings.local.json` | **yes** — same reason |
| **managed** | deployed by an administrator, not modifiable | managed settings | depends — see below |

**Verified here.** Installing `genscalator@bjornregnell` in the personal profile
wrote `~/.claude-personal/plugins/installed_plugins.json` with `"scope": "user"`
and registered the `bjornregnell` marketplace in that profile's
`known_marketplaces.json`. The work profile has no `installed_plugins.json` at
all and knows only `claude-plugins-official`. So a user-scope plugin installed
on one plan is genuinely invisible to the other.

**To use the same plugin on both plans** you install it twice — and add the
marketplace twice, since `known_marketplaces.json` is per profile too:

```
claude-profile work
/plugin marketplace add bjornregnell/genscalator
/plugin install genscalator@bjornregnell
```

**Project and local scope are the crossing points.** They are repository state,
not account state, so any profile that opens that repository picks them up. That
is usually what you want for a shared project, but it does mean a plugin you
added "just for this repo" on the personal plan will also load when you open the
same repo on the work plan. If that matters — a plugin pulling in an MCP server
that should not see work material, say — keep it at user scope on the profile
that should have it.

Two Syncthing interactions follow from that. A repo under `~/asd` carries its
`.claude/settings.json` to every machine, so a project-scope plugin propagates
with the folder; and `.claude/settings.local.json` syncs too, which is fine
because it is still you on both machines.

**Team marketplaces** use the same mechanism: an `extraKnownMarketplaces` block
in a repo's `.claude/settings.json` registers the catalog for anyone who trusts
that folder. Since v2.1.195 this only *adds* the marketplace — a plugin from an
external source still has to be installed, and Claude Code reports it as not
installed with the command to run.

**Managed scope** depends on delivery. A `managed-settings.json` file on the
machine is outside every config home and so applies to both profiles; managed
settings delivered from the claude.ai console arrive per account and so differ
between them. Your work profile already carries account-delivered org policy in
`policy-limits.json`, which the personal profile does not.

**Three smaller traps:**

- Removing a marketplace **uninstalls every plugin installed from it** — and
  removal is per profile, so doing it on one plan leaves the other untouched.
- The documented cache-clearing fix, `rm -rf ~/.claude/plugins/cache`, is
  written for the stock layout. On the personal profile the path is
  `~/.claude-personal/plugins/cache`. Any plugin instruction naming `~/.claude`
  needs the same translation.
- `--plugin-dir` and `--plugin-url` load a plugin for one session regardless of
  profile. Useful for trying something on the work plan without installing it
  there; pass it through with `claude-profile work -- --plugin-dir ./p`.

**What does *not* move:** the Claude Code installation itself. On this machine
`~/.local/bin/claude` resolves to `~/.local/share/claude/versions/2.1.272`, and
both profiles run that same binary. An update upgrades both plans at once; there
is no per-profile pinning of the CLI version.

### genscalator on this setup

**What it is.** `genscalator@bjornregnell` is the author's own Claude Code plugin: a
typed Scala toolbox (`tt`), a set of skills, the `gs` do-what-I-mean commands,
and a statusline renderer. It was written against the *stock* single-profile
layout, so a few of its assumptions need translating here. Verified on this
machine 2026-09-15 against genscalator 0.10.2.

**There are two independent ways to have it installed**, and this setup has
both:

| form | where | reaches | installed here |
|---|---|---|---|
| **plugin** | `<configHome>/plugins/cache/bjornregnell/genscalator/<version>/` | the profile it was installed on | personal only |
| **standalone** | `~/.genscalator/` (from `get-genscalator.sc`) | any shell, any profile — it is outside every config home | yes, `v0.10.2`, but its `bin/` is **not on PATH** |

The plugin form is what gives you the skills and `/gs`-style commands inside a
session. The standalone form is just the toolbox binary. They are separate
installs at separate versions; keeping them in step is manual.

**Installing the plugin on a profile.** User scope, so it must be done once per
profile — marketplace included, since `known_marketplaces.json` is per config
home too (§3):

```
claude-profile personal          # or work
/plugin marketplace add bjornregnell/genscalator
/plugin install genscalator@bjornregnell
/reload-plugins
```

**The PATH trap.** `tt` is on PATH only because Claude Code prepends the enabled
plugin's `bin/` directory for the session:

```
/home/you/.claude-personal/plugins/cache/bjornregnell/genscalator/0.10.2/bin/tt
```

Two properties of that path matter. It is **per profile** — the work profile has
no `installed_plugins.json` at all, so `tt` does not resolve there. And it is
**version-pinned** — `0.10.2` is in the path, so it moves on every plugin
update, which rules out hardcoding it anywhere.

**That PATH is the Bash tool's, not the session's.** This is the part that costs
an hour. Claude Code adds the plugin's `bin/` to the environment it gives the
*Bash tool*; the `claude` process's own PATH is untouched, and a `statusLine`
command is spawned from that process. Verified on the running session:

```
$ tr '\0' '\n' < /proc/<claude-pid>/environ | grep ^PATH
PATH=/home/you/.local/bin:...          # no plugin bin/ anywhere

$ env -i HOME=$HOME PATH=/home/you/.local/bin:/usr/bin:/bin sh -c 'tt statusline'
sh: 1: tt: not found
```

So a bare `tt` in `statusLine` **cannot work on either profile**, plugin
installed or not, and it fails the quiet way: a failing statusline command
renders a blank line, not an error. genscalator's own SM209 capture facility
settles it in one step — `touch ~/.claude/gs-statusline-dump-on` and see whether
`~/.claude/gs-statusline-last.json` appears. No file means the command never
ran; a file means it ran and the problem is elsewhere.

**Use an absolute path to the standalone install instead:**

```json
"statusLine": {
  "type": "command",
  "command": "$HOME/.genscalator/bin/tt statusline --mode-line --box-line"
}
```

`$HOME` expands because the command goes through a shell, so this stays
machine-independent. It also sidesteps the plugin entirely — which is what makes
it work on the work profile, where genscalator is not installed.

**And it is the faster binary by two orders of magnitude.** The plugin's `bin/tt`
is a bash wrapper around `tools/tt`, which goes through `scala-cli`; the
standalone install is a GraalVM native image. Measured here, same machine, same
0.10.2:

| `tt statusline` via | wall time |
|---|---|
| `~/.genscalator/bin/tt` (native) | **8 ms** |
| `tt` (plugin launcher → scala-cli) | **656 ms** |

For a command the harness re-runs on every render that is not a micro-optimisation.
There is a second-order effect too: the scala-cli route starts a JVM per render,
which line 3 of the statusline then dutifully reports as box load — the tool
inflating the number it is measuring.

**Wiring the statusline.** It belongs in `config/shared.json` like any other
plan-independent key, with the absolute path from the PATH trap above:

```json
"statusLine": {
  "type": "command",
  "command": "$HOME/.genscalator/bin/tt statusline --mode-line --box-line"
}
```

Three lines: the genscalator status line, the declared-modes line, and a
measured box-health line from `/proc`. Each flag is optional and they toggle
independently (`gs status line|mode|box on|off`, or just edit the key).

Two things cost time here, both observed live:

- **Relaunch after changing it** — `claude-profile personal -- --continue` picks
  the session back up with the new setting. *Unverified:* whether `/hooks`
  reloads a changed `statusLine` is still unknown. This file previously asserted
  that it does not, on the strength of a `/hooks` run that changed nothing — but
  that session's command was unresolvable anyway (the PATH trap above), so the
  observation proves nothing about `/hooks`. Relaunching is known to work; treat
  the rest as untested.
- **Do not put it in `<configHome>/settings.json`.** It was tried there first
  and never appeared. That file is level 5 and is also where Claude Code writes
  its own `/config` changes (invariant 2), so it is the wrong home for anything
  you want to keep; `config/shared.json` is regenerated into
  `effective-settings.json` at every launch and survives.

Note that `claude-profile show <profile>` **prints** the merge without writing
it — `effective-settings.json` is only rewritten by an actual launch. So after
editing an overlay, `show` reflects the change immediately while the running
session still holds the old merged file.

**`gs allow` writes repository state, not account state.** It merges a `tt`
allowlist into the repo's `.claude/settings.local.json` — level 3, and a
crossing point per the table above, so both profiles pick it up when they open
that repo, and it travels with Syncthing. That is usually fine: `permissions.allow`
is a list key, so it *combines* with the baseline rules in `config/shared.json`
rather than replacing them (§4).

**State files ignore `CLAUDE_CONFIG_DIR`.** genscalator resolves its own state
from `$HOME/.claude` literally, in six places (0.10.2):

| file | what it holds |
|---|---|
| `~/.claude/gs-modes` | declared modes (`tt mode`, the statusline's line 2) |
| `~/.claude/gs-limits.json` | declared plan limits (`tt limit`) |
| `~/.claude/gs-sessions` | session names |
| `~/.claude/projects/<slug>/memory/` | `tt memory` |
| `~/.claude/plugins/cache` | where `tt skillcheck` probes for skills when run from a native install |

So on the **personal** profile all of this lands in the **work** profile's
config home. Mostly harmless for the state files — mode and session state is
session-scoped, so the two plans do not collide in content — but it is state
written to the wrong place, and the last row produces a confidently wrong
answer. Verified here:

```
$ ~/.genscalator/bin/tt skillcheck
skillcheck: not a skills directory: /home/you/.genscalator/skills
...
  (no plugin-cache skills/ found under /home/you/.claude/plugins/cache)
```

The skills it says are absent are installed, at
`~/.claude-personal/plugins/cache/bjornregnell/genscalator/0.10.2/skills`. The
probe looks in the stock config home, which on this profile is the *other*
plan's.

Workarounds exist per call (`tt statusline --modes-file F --limits-file F`,
`tt mode --file F`, `tt skillcheck --skills <dir>`); the real fix belongs
upstream, and is the narrow half of the contribution idea below.

### The trap that would cost you your work sign-in

With `CLAUDE_CONFIG_DIR` **unset**, the global config file is `~/.claude.json` —
*beside* `~/.claude`, not inside it. With `CLAUDE_CONFIG_DIR=$HOME/.claude` set,
Claude Code looks for `$HOME/.claude/.claude.json` — a **different, empty file**.
So "just point the work profile at its existing directory" silently orphans the
current sign-in, project trust decisions and history.

Therefore: **the work profile keeps the stock layout and never sets the variable
at all.** Only the new personal profile gets a `CLAUDE_CONFIG_DIR`. The launcher
in §5 encodes this as a `Profile` whose `configDir` is `None`, and every path it
derives goes through `configHome` / `globalJson` so the two layouts stay
distinguishable.

How this surfaced is worth recording, because the failure was quiet: the first
version of `claude-who` resolved the account file as `<configDir>/.claude.json`
for every profile, and duly reported the work profile as *"not signed in yet"* —
while the session was, visibly, signed in. The tool was reading the file that
`CLAUDE_CONFIG_DIR=$HOME/.claude` *would* have created, and finding nothing. A
wrapper written to the same assumption would not have printed a warning; it
would have opened a signed-out Claude Code and offered a login prompt, with the
real `~/.claude.json` still sitting there untouched. If you adapt this setup for
a third account, keep a read-only tool like `claude-who` in the loop: it fails
loudly where a launcher fails silently.

---

## 4. Settings: one baseline, per-plan differences on top

### Precedence (from the docs, highest wins)

| # | level | file |
|---|---|---|
| 1 | Managed settings | `managed-settings.json`, MDM, or the claude.ai console |
| 2 | Command line | `claude --settings <file-or-json>` |
| 3 | Project local | `.claude/settings.local.json` |
| 4 | Shared project | `.claude/settings.json` |
| 5 | User | `~/.claude/settings.json` (moves with `CLAUDE_CONFIG_DIR`) |

Merging is key-by-key: a level supplies the keys it sets and inherits the rest.
**List keys such as `permissions.allow` combine across levels** instead of
replacing each other. Four model keys are exceptions that take their whole value
from the highest level that sets them: `fallbackModel`, `modelPicker`,
`availableModels`, `modelSettings`.

Environment variables are not a level: `ANTHROPIC_MODEL` beats the `model` key in
any file, while `ANTHROPIC_DEFAULT_MODEL` applies only if no file sets `model`.
The launcher scrubs both so the per-profile `model` key stays authoritative.

### Why the obvious layouts don't work

- **Symlink one shared `settings.json` into both config homes.** Breaks, because
  Claude Code *writes* to the user settings file itself — `/config` changes like
  theme land there. A `/config` change on one plan would silently rewrite the
  other. It also leaves nowhere for per-plan differences, since both profiles
  would occupy the same precedence level.
- **Shared file via `--settings`, differences in user settings.** Wrong
  direction: `--settings` is level 2 and *outranks* user settings at level 5, so
  the shared baseline would override the per-plan keys.

### The layout that does work

Compute the merge yourself and hand the result in at level 2:

```
config/shared.json  ──┐
                      ├─► deep merge ─► <configHome>/effective-settings.json
config/<profile>.json ┘                        │
                                               ▼
                       claude --settings <that file>     (level 2)
                       CLAUDE_CONFIG_DIR=<configHome>    (personal only)
```

Properties this buys you:

- One baseline, edited once, applied to both plans.
- Per-plan overrides that genuinely win, because they are merged in last.
- Each profile's own `~/.claude/settings.json` (level 5) stays free for Claude
  Code's own `/config` writes — they still work per profile and are not clobbered.
- Project-level files (levels 3–4) keep outranking your baseline, as they should.
- The merged file is written into the profile's own config home, so a stale one
  can never leak across plans, and `claude-profile show <p>` prints it for
  inspection before launch.

### What belongs where

`config/shared.json` — anything plan-independent: theme, `tui`, permission
allow-rules, hooks, telemetry, statusline.

`config/work.json` — `"model": "opus[1m]"`. Do **not** put `fable` or `best`
here; the seat cannot serve them without credits the org has disabled.

`config/personal.json` — `"model": "opus[1m]"` as a conservative default. Fable
is available here; switch per session with `/model fable` rather than defaulting
to it, since it draws on the 50%-of-weekly-limit allowance.

---

## 5. The tooling

Scala 3, built with scala-cli to GraalVM native images, installed on `PATH`.
Two binaries from one source directory.

```
~/asd/claude/
├── claude-profile-draft.md this file
├── config/
│   ├── profiles.json       which profiles exist and where they live
│   ├── shared.json         baseline, both plans
│   ├── work.json           work overrides
│   └── personal.json       personal overrides
├── scala/
│   ├── project.scala       build directives, shared by every main
│   ├── multiplan.scala     registry, merge, launch, account reader
│   ├── launcher.scala      main: claude-profile
│   ├── who.scala           main: claude-who
│   └── docsync.scala       main: regenerates the code blocks in this file
└── bin/                    build output — put this on PATH
```

### `config/profiles.json`

A profile with **no** `configDir` uses the stock layout and leaves
`CLAUDE_CONFIG_DIR` alone — see the trap in §3.

```json
{
  "shared": ["config/shared.json"],
  "profiles": {
    "work": {
      "overlays": ["config/work.json"],
      "description": "University org — Claude Team for Scientists, standard seat (stock ~/.claude layout)"
    },
    "personal": {
      "configDir": "~/.claude-personal",
      "overlays": ["config/personal.json"],
      "description": "Private Max 5x"
    }
  }
}
```

### `scala/project.scala` — build directives

`packaging.graalvmJvmId` and `packaging.graalvmArgs` are the two directives that
control the native build; `mainClass` is deliberately *not* a directive here,
because one source directory produces two binaries and the main class is chosen
per `package` invocation.

```scala
// Build configuration shared by every main in this directory.
//
// Build (one native binary per main class):
//   scala-cli --power package scala -o bin/claude-profile --native-image -f \
//     --main-class claude.multiplan.LauncherMain
//   scala-cli --power package scala -o bin/claude-who     --native-image -f \
//     --main-class claude.multiplan.WhoMain

//> using scala 3.9.0
//> using dep com.lihaoyi::ujson:4.4.3
//> using options -deprecation -feature -Wunused:all
//> using packaging.graalvmJvmId graalvm-community:25.0.2
//> using packaging.graalvmArgs --no-fallback -O2 -H:+ReportExceptionStackTraces
```

`ujson` rather than full upickle: the merge is structural, not case-class
shaped, and a smaller macro-only dependency keeps the native image free of
reflection configuration.

### `scala/multiplan.scala` — the library

```scala
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
```

`inheritIO()` is what makes this work as a launcher: the child gets the real
terminal, so the full-screen TUI, colours and Ctrl-C behave exactly as if you
had typed `claude` yourself. The parent just waits and forwards the exit code.

### `scala/launcher.scala` — `claude-profile`

```scala
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
```

### `scala/who.scala` — `claude-who`

```scala
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
```

Only `max_5x` / `max_20x` / `pro` in `seatNotes` are guesses; `team_labs_standard`
is the value actually observed on this machine. Run `claude-who` after signing in
personally and correct the map to whatever the Max account really reports.

### Prerequisites for the native build

`native-image` is not self-contained: it compiles your code and then **links**
the result with the system C toolchain. GraalVM itself is fetched automatically
by scala-cli, but the toolchain is not, and on a fresh Ubuntu it is missing.

Install it once:

```
sudo apt install build-essential zlib1g-dev
```

| package | why it is needed |
|---|---|
| `build-essential` | pulls in `gcc`, `g++`, `make` and `libc6-dev`. GraalVM looks specifically for `gcc` on `PATH` and aborts if it is absent, even when another compiler exists |
| `zlib1g-dev` | supplies `/usr/include/zlib.h` and the unversioned `/usr/lib/x86_64-linux-gnu/libz.so` symlink. The runtime `libz.so.1` is already present on any Ubuntu, but the linker resolves `-lz` only through the unversioned name |

Check it took:

```
gcc --version                             # 13.3.0 here
ls /usr/include/zlib.h                    # must exist
ls /usr/lib/x86_64-linux-gnu/libz.so      # the symlink, not just .so.1
```

On other distributions the equivalents are `gcc glibc-devel zlib-devel`
(Fedora/RHEL) or `base-devel zlib` (Arch). GraalVM's own prerequisites page
lists them per platform.

Everything else — GraalVM 25.0.2 itself, roughly 700 MB into
`~/.cache/coursier` — is downloaded by scala-cli on first build. Budget a few
minutes for that download once, then ~30 s per image.

#### If you cannot install packages

Both problems have root-free workarounds, kept here in case you build on a
machine where you are not admin:

- **No `gcc`,** but clang present: add
  `--graalvm-args --native-compiler-path=/usr/bin/clang`. Clang handles the
  entire build; only GraalVM's `gcc`-by-name check is the obstacle.
- **No `libz.so`:** create the missing symlink somewhere you own and add it to
  the link path.

  ```
  mkdir -p ~/asd/claude/.nativelib
  ln -sf /usr/lib/x86_64-linux-gnu/libz.so.1 ~/asd/claude/.nativelib/libz.so
  ```

  then `--graalvm-args --native-compiler-options=-L$HOME/asd/claude/.nativelib`.
  This is sound rather than a hack: the linker follows the symlink, reads the
  SONAME, and records a normal `libz.so.1` dependency — `ldd` output is
  identical either way.

This was how the binaries were first built here, before `build-essential` and
`zlib1g-dev` were installed. With the packages in place the workaround directory
is unnecessary and has been deleted.

### `scala/docsync.scala` — keeping this file honest

Run it after every source change; see §6.

```scala
package claude.multiplan

import java.nio.file.Files
import scala.util.matching.Regex

/** claude-profile-draft.md embeds the full text of the sources in this directory. Those
  * blocks are generated, and this keeps them honest.
  *
  *   scala-cli run scala --main-class claude.multiplan.DocSyncMain
  *   scala-cli run scala --main-class claude.multiplan.DocSyncMain -- --check
  *
  * `--check` writes nothing and exits 1 if any block has drifted, so it works
  * as a pre-commit or CI gate. Blocks are rewritten back-to-front so that
  * replacing one does not invalidate the offsets of the ones above it.
  */
object DocSyncMain:

  private val Heading: Regex = """(?m)^### `scala/([A-Za-z0-9_.]+)`""".r
  private val Fence = "```scala\n"

  def main(args: Array[String]): Unit =
    val check = args.contains("--check")
    val doc = Root.dir.resolve("claude-profile-draft.md")
    val srcDir = Root.dir.resolve("scala")

    if !Files.exists(doc) then
      Console.err.println(s"docsync: no $doc")
      sys.exit(2)

    var text = Files.readString(doc)
    var drifted = List.empty[String]
    var matched = 0

    for m <- Heading.findAllMatchIn(text).toList.reverse do
      val name = m.group(1)
      val file = srcDir.resolve(name)
      if !Files.exists(file) then
        Console.err.println(s"docsync: $name is embedded but missing from ${srcDir}")
        sys.exit(2)
      matched += 1
      val fenceStart = text.indexOf(Fence, m.end)
      if fenceStart < 0 then
        Console.err.println(s"docsync: no ```scala block after the heading for $name")
        sys.exit(2)
      val bodyStart = fenceStart + Fence.length
      val bodyEnd = text.indexOf("\n```", bodyStart)
      if bodyEnd < 0 then
        Console.err.println(s"docsync: unterminated block for $name")
        sys.exit(2)

      val embedded = text.substring(bodyStart, bodyEnd)
      val actual = Files.readString(file).stripTrailing()
      if embedded == actual then println(s"ok       $name")
      else
        drifted = name :: drifted
        val embeddedLines = embedded.linesIterator.size
        val actualLines = actual.linesIterator.size
        println(s"drifted  $name  ($embeddedLines -> $actualLines lines)")
        if !check then text = text.substring(0, bodyStart) + actual + text.substring(bodyEnd)

    if matched == 0 then
      Console.err.println("docsync: found no embedded source blocks -- has the heading format changed?")
      sys.exit(2)

    if drifted.isEmpty then println(s"\n$matched block(s) embedded, all in sync")
    else if check then
      println(s"\n${drifted.size} block(s) out of sync: ${drifted.mkString(", ")}")
      println("run without --check to rewrite them")
      sys.exit(1)
    else
      Files.writeString(doc, text)
      println(s"\nrewrote ${drifted.size} block(s) in $doc")
```

### Build and install

**Verified working on this machine**, with the prerequisites above installed and
no extra flags — `--no-fallback` and `-O2` come from `project.scala`:

```
scala-cli --power package scala -o bin/claude-profile --native-image -f \
  --main-class claude.multiplan.LauncherMain

scala-cli --power package scala -o bin/claude-who --native-image -f \
  --main-class claude.multiplan.WhoMain
```

Then put `~/asd/claude/bin` on `PATH`.

Result: two ~15 MB executables that start in about **5 ms** and link only
`libz.so.1`, `libc.so.6` and the loader. About 30 s per image once GraalVM is
cached.

Check the exit status directly, not through a pipe — see the gotcha about
`PIPESTATUS`.

#### `--no-fallback` was never a problem

Worth recording, because it is the usual suspect: the `ujson` dependency needed
**no** reflection configuration. Even the very first attempt, before the
toolchain existed, reached the linker with a clean analysis — which is the
evidence that the pure-Scala, macro-based JSON choice paid off. Had it not,
native-image would have failed earlier and named the class to register.

#### Iterating without the native build

```
scala-cli run scala --main-class claude.multiplan.LauncherMain -- list
```

Same behaviour, JVM startup instead of 5 ms. Use this while editing; build
native only when you are done.

### First run of the personal profile

```
claude-profile personal        # then /login with the private email
claude-who                     # confirm the two accounts are distinct
claude-profile doctor
```

### Everyday use

```
claude-profile work                       # the science seat
claude-profile personal                   # the Max 5x seat
claude-profile personal -- -p "summarise" # arguments after -- go to claude
claude-profile show personal              # what settings will actually apply
```

---

### Syncthing: keeping build output out of the sync

`~/asd` is a Syncthing folder (id `ojo9p-jyf6f`), so the 30 MB of native
binaries and the `.scala-build` / `.bsp` caches would otherwise be replicated to
every machine. They are excluded by `~/asd/.stignore`.

**How Syncthing ignores work**, per the docs:

- The ignore file must sit in the **folder root** — here `~/asd/.stignore`, not
  in `~/asd/claude`. Per-subdirectory ignore files are not supported.
- `.stignore` itself is **never synced**. To share patterns across machines,
  keep them in an ordinary (synced) file and pull it in with `#include`. That is
  the split used here: `~/asd/.stignore` contains only
  `#include .stignore-shared`, and `~/asd/.stignore-shared` holds the patterns
  and travels with the folder. On a new machine you recreate the one-line
  `.stignore` and everything else arrives by itself.
- Patterns are relative to the folder root. A pattern **without** a leading `/`
  matches at any depth; with a leading `/` it is anchored to the root only.
- **The first matching pattern decides**, so any `!` un-ignore must be listed
  *before* the broader pattern it carves an exception out of.
- `(?d)` marks a file as deletable — it lets Syncthing remove the ignored file
  when it would otherwise block removing a directory. Worth having on every
  build-output pattern, since those directories do get deleted.

**The anchoring detail that matters here.** `~/asd/bin` contains your own
scripts (`evo-kill`, `lucat-cp`, `update`). An unanchored `bin` pattern would
match at every depth and silently stop syncing them. The pattern is therefore
written `/claude/bin`, anchored to the folder root. Syncthing's own expansion
confirms the difference:

```
GET /rest/db/ignores?folder=ojo9p-jyf6f

"(?d).scala-build", "(?d)**/.scala-build",   <- unanchored: matches any depth
"(?d)/claude/bin",  "(?d)/claude/bin/**"     <- anchored: root only
```

That endpoint is the way to check a change actually parsed; `"error": null` in
the response means the patterns and the `#include` both resolved.

```
C=~/.config/syncthing/config.xml
K=$(grep -oP '(?<=<apikey>)[^<]+' "$C" | head -1)
curl -s -H "X-API-Key: $K" \
  "http://127.0.0.1:8384/rest/db/ignores?folder=ojo9p-jyf6f"
```

**Why the binaries are excluded rather than synced.** They are x86-64 Linux
executables of ~15 MB each, rebuilt in 30 s, and would be wrong on a machine of
another architecture. The sources and `config/*.json` sync; `bin/` is rebuilt
per machine, which means each machine needs the prerequisites above. If you
would rather sync the binaries to a machine that cannot build them, delete the
`/claude/bin` line — but keep the anchoring in mind if you do.

**One pattern to keep an eye on:** `target` is unanchored, which is right for
sbt and Mill output but would also match a directory you genuinely called
`target`. Nothing in `~/asd` does today.

---

### Verified working, both plans live

Checked on 2026-09-15 with both accounts signed in:

| check | result |
|---|---|
| distinct accounts | `work@university.example` (the university org) vs `personal@example.com` |
| distinct plans | Team for Scientists standard seat vs Max 5x |
| distinct OAuth tokens | different SHA-256; `subscriptionType` `team` vs `max` |
| distinct config homes | `~/.claude` vs `~/.claude-personal`, each with its own `sessions/`, `projects/`, `plugins/`, `settings.json` |
| work config untouched | `~/.claude.json` unchanged while the personal profile was created |
| settings merge applied | `~/.claude-personal/effective-settings.json` carries the shared baseline plus `model: opus[1m]` |
| `claude-profile doctor` | all seven checks pass, exit 0 |

The most informative result is the last one in the file list. Claude Code's own
onboarding wrote `{"theme": "dark"}` into
`~/.claude-personal/settings.json` — the **profile's own** level-5 file — while
`config/shared.json` stayed exactly as written. That is the settings
architecture in §4 working as intended: `/config` changes remain per profile and
cannot reach across to the other plan, because the shared baseline is never the
file Claude Code writes to.

---

## 6. Working on this machinery

### The five invariants

Each of these looks like something a tidy-minded session would "clean up", and
each one silently breaks a profile rather than failing loudly.

1. **The work profile must never set `CLAUDE_CONFIG_DIR`.** Its config home is
   the stock `~/.claude`, whose global file is `~/.claude.json` — *beside* the
   directory. Setting `CLAUDE_CONFIG_DIR=$HOME/.claude` makes Claude Code read
   `~/.claude/.claude.json`, a different empty file, orphaning the sign-in. This
   is why `Profile.configDir` is an `Option` and why `configHome` and
   `globalJson` are separate accessors. Do not collapse them. (§3)

2. **Never symlink one `settings.json` into both config homes.** Claude Code
   *writes* to the user settings file — `/config` changes land there. A shared
   file means a `/config` change on one plan rewrites the other. The baseline
   reaches a session through `--settings` instead, and each profile's own
   `settings.json` is left alone for Claude Code's writes. Verified: onboarding
   wrote `theme` into `~/.claude-personal/settings.json` while
   `config/shared.json` stayed byte-identical. (§4)

3. **Per-plan overrides must merge in last.** `--settings` is precedence level
   2 and outranks user settings at level 5, so the baseline cannot live there —
   it would beat the per-plan keys. The launcher merges `shared.json` then the
   profile overlay, and passes the single result. Reversing that order inverts
   the whole design. (§4)

4. **Syncthing patterns are unanchored by default.** `bin` matches `~/asd/bin`
   — your own scripts — as well as `~/asd/claude/bin`. The pattern is
   `/claude/bin` with a leading slash for that reason. (§5, Syncthing)

5. **The plan is not in one field.** A Team seat states it in `seatTier`; a Max
   account leaves `seatTier` null and states it in `organizationRateLimitTier`.
   Code that reads only `seatTier` reports a Max account as having no plan. (§1)

### Making a change

Edit sources in `scala/`, iterate on the JVM, and only then rebuild:

```
cd ~/asd/claude
scala-cli run scala --main-class claude.multiplan.LauncherMain -- list
scala-cli run scala --main-class claude.multiplan.WhoMain
```

When it behaves, rebuild the native images and re-verify:

```
scala-cli --power package scala -o bin/claude-profile --native-image -f \
  --main-class claude.multiplan.LauncherMain
scala-cli --power package scala -o bin/claude-who --native-image -f \
  --main-class claude.multiplan.WhoMain
claude-who && claude-profile doctor
```

`bin/` is symlinked from `~/.local/bin`, so a rebuild takes effect immediately
with no reinstall step.

**Check the exit status directly.** `scala-cli package ... | tail -30` reports
`tail`'s status, so a failed build looks successful — this actually happened
here. Redirect to a log file, or read `${PIPESTATUS[0]}`.

### Editing these files: `tmp/`, not `python3 - <<EOF`

Edits to the documents here go through a small scratch tool rather than an
inline interpreter script:

```
scala-cli run tmp/anchored-edit.scala -- claude-profile-draft.md tmp/some.patch [--dry-run]
```

The patch file is hunks of `=== OLD` / `=== NEW` blocks. Each anchor must occur
**exactly once** or nothing is written at all — the all-or-nothing rule, because
a half-applied batch is the failure that makes you re-read the whole file to
find out where you are. `--dry-run` reports the line each hunk would land on.

Why this exists rather than a heredoc: a heredoc edit shows the human a shell
command and a shrug of output, while the patch file is readable, re-runnable and
kept. The habit it replaces is measured in `issue+4-genscalator.md` — **24 of 95
Bash calls** in one session were `python3` heredocs editing these very files,
**1574 lines of python written, run once and discarded**.

(This paragraph said "16 of ~40" until 2026-09-24. That was the first, wrong
measurement: the counts were halved on the theory that the transcript records
each call twice, which it does not — 117 `tool_use` records against 118
`tool_result`. The corrected figures are in `issue+4-genscalator.md`, which also
records how the error was found. Noted rather than silently fixed, because the
wrong number had already been copied into a later session's recap and into a
handover file before anyone re-derived it — which is exactly what an uncorrected
figure in a durable record does.)

`tmp/` holds the tool plus its two fixtures (`t-sample.txt`, `t-good.patch`,
`t-ambiguous.patch` — the second proves the uniqueness refusal). It is scratch,
not build output: it survives a restart on purpose, and it is the right home for
the next one-off tool too. Nothing in `tmp/` is load-bearing for the profiles.

### Keeping this file in sync with the sources

§5 embeds the full text of all three Scala files. Those blocks are **generated**.
After changing anything in `scala/`, regenerate rather than hand-editing:

```
scala-cli run scala --main-class claude.multiplan.DocSyncMain
```

It rewrites each `### \`scala/<file>\`` block from the file on disk and reports
what changed; with `--check` it only reports, exiting 1 on drift, which makes it
usable as a pre-commit or CI gate. The first version of this doc was synced by
an ad-hoc script and the blocks had already drifted by two whitespace edits —
hence a real tool.

### Adding a third profile

Add an entry to `config/profiles.json` with its own `configDir` and overlay
file, then `claude-profile <name>` and `/login`. Nothing else needs changing —
the registry is data, not code. Points to watch: give it a *new* directory
(never an existing config home, per invariant 1), and check `claude-who`
recognises its plan — if it prints an unfamiliar `orgType`, extend
`Account.plan` rather than leaving it to fall through to the raw string.

### TODO: publish this as a standalone repo

Nothing here is specific to the author's two accounts except the facts in §1, so the
machinery is worth its own public repository — working title
**`bjornregnell/claude-multi-profile`**: run several Claude plans from one Linux
user, with a profile registry, a deep-merged settings baseline, and a launcher
that keeps each sign-in intact. It is a real gap — the stock answer is
"`CLAUDE_CONFIG_DIR` and good luck", and invariant 1 is exactly the trap that
advice walks you into.

What would move: `scala/` (five sources, one directory), `config/` as *example*
overlays, and this file as the README or `docs/`. `bin/` stays out — native
images are build output.

Prep, in rough order:

1. **Scrub the private facts.** §1 is an account inventory: work email, the org,
   seat tiers, `oauthAccount` shapes. Split it — the *method* (how to identify a
   plan from `.claude.json`, invariant 5) is the reusable part and belongs in the
   repo; the *answers for this machine* are private and stay here, or in a
   gitignored `local-notes.md`. Same for `~/asd` and `/home/you` paths in
   prose, and for the Syncthing section, which is this machine's setup rather
   than anyone else's.
2. **Check the code is already location-independent.** Mostly it is:
   `multiplan.scala:20` reads `CLAUDE_MULTIPLAN_ROOT` and only *defaults* to
   `~/asd/claude`. Two prose spots still hardcode it — `launcher.scala:18`'s
   usage text and this file — and the default itself should probably become
   `~/.config/claude-multi-profile` with `~/asd/claude` as the local override.
3. **Decide what §5 becomes.** Embedding all five sources in the doc is right
   *here*, where the doc travels alone and Syncthing carries no build output. In
   a repo where the sources sit next to the README it is duplication that
   `DocSyncMain --check` then has to police forever. Excerpt the interesting
   parts and link the files; keep DocSync for whatever stays embedded.
4. **Ship the build.** `scala-cli` build instructions, GraalVM prerequisites,
   the `PIPESTATUS` gotcha, a `doctor` run as the smoke test, and a CI job
   running `DocSyncMain --check` plus `claude-profile list|show`.
5. **License and cross-links.** Point at genscalator from the README, and file
   the reverse pointer once the profile-awareness question there is settled.

Open question worth deciding before publishing, not after: whether this stays a
separate tool or eventually lands inside genscalator. See the upstreaming bullet
below — the two answers are not exclusive (a standalone repo now, a `tt`
front-end later, if genscalator's maintainer wants one), but the README should
say which one it is betting on.

### TODO: promote the remaining issue drafts to real genscalator issues

**Two of the five are done and awaiting the human submit step** (2026-09-24).
Drafts 1 and 3 were re-verified against `5adb395` and rewritten as
`filing/issue-067-*.md` and `filing/issue-068-*.md` — ready to copy into a
checkout and open a PR. Filing itself is a human action by genscalator's own
rule (`CONTRIBUTING.md`, "For agents"), so the agent stopped there.

| draft | status | filed as |
|---|---|---|
| `filing/superseded/issue+1-…` | **ready to submit** | `filing/issue-067-config-home-hardcoded-ignores-claude-config-dir.md` |
| `issue+2-genscalator.md` | still a draft; needs the issue-vs-report decision below | — |
| `filing/superseded/issue+3-…` | **ready to submit** | `filing/issue-068-statusline-wiring-bare-tt-never-resolves.md` |
| `issue+4-genscalator.md` | still a draft | — |
| `issue+5-genscalator.md` | still a draft | — |

Drafts 1 and 3 were moved to `filing/superseded/` on 2026-09-24: two divergent
copies of the same document sat side by side with nothing marking which was
live, and the ambiguity was the hazard. They are kept rather than deleted
because 067 and 068's re-verification comments describe drift *relative to
them*; `filing/superseded/README.md` says so and says when they can go.

Drafts 2, 4 and 5 no longer name a target number. They said 025 / 027 / 028
until the same day, and the lesson of the renumber is that **a number written
into a draft that then sits for days is a number that will be wrong** — so their
headers now say `issue-NNN-…`, with the number to be taken at filing against the
live tree and the open-PR list. None of the three has been re-verified against
`5adb395`; assume their line numbers have drifted the way draft 1's did.

What the re-verification changed — all of it drift the drafts could not have
known about, and the reason the five checks exist:

* **Numbering was badly stale.** The drafts assumed 024–026 were free. The
  repository is at **066**, so the two became **067** and **068**. Both numbers
  were then confirmed free against the forge itself: **zero open PRs**, and no
  `issue-067` or `issue-068` anywhere in the `main` tree at `5adb395`. This is
  the reservation gap `reqts/issues/README.md` warns a file scan cannot see, and
  it needed no credentials — a public repository's PRs and tree are readable
  anonymously through the forge API, which is worth remembering the next time
  `gh auth` has expired.
* **Three of six line numbers in draft 1 had moved** — `sessionstore.scala`
  39→40, `statusline.scala` 559→596, `lib.scala` 203→231 — and the SM209 marker
  note 623→660. The defect itself was untouched: the same seven hardcoded
  `".claude"` sites are still there.
* **Draft 3's sweep was too narrow.** It listed four wiring sites; there are
  **eight**, including both header comments in `statusline.scala` and
  `docs/clock-mechanics.md:24`, which nothing had noticed. It also claimed a
  wiring line in `README.md` — there is none.
* **The `tt skillcheck` reproduction still reproduces byte for byte**, nine days
  on, which is draft 1's strongest evidence and was worth re-taking.
* **No duplicates.** Nothing in `open/` or `closed/` mentions
  `CLAUDE_CONFIG_DIR`, and issue 064 (statusline session chip) does not overlap
  either draft.

Each filed issue carries a dated re-verification comment recording the drift, so
the delta from the drafting baseline stays auditable rather than being quietly
corrected.

They are already written in genscalator's issue format, so filing is a copy plus
a PR — genscalator tracks issues as files in the repository (`reqts/issues/`),
not on a forge, which is exactly why drafting them locally costs nothing and
loses nothing if they sit here for months.

Before filing, five checks — the drafts were written against the **plugin cache
at 0.10.2**, not a checkout, and both facts and numbers move:

1. **Re-verify the line numbers against current `HEAD`.** The six call sites in
   draft 1 are cited as `mode.scala:70`, `limitstore.scala:12`,
   `sessionstore.scala:39`, `statusline.scala:559`, `memory.scala:62`,
   `lib.scala:203`. Re-run `grep -rn '".claude"' tools/*.scala` and fix any
   drift — issue 022's own provenance note is the standard to match.
2. **Re-check the next free `NNN`** across `open/` *and* `closed/`. 024/025 were
   free when the drafts were written; per `reqts/issues/README.md` the later PR
   renumbers if two claim the same number.
3. **Re-run the `tt skillcheck` reproduction** and paste the current output. It
   is the strongest evidence in draft 1 and it is cheap to re-take.
4. **Update the comment timestamps** in the `## Discussion` sections — they read
   `2026-09-15 17:33`. The convention is
   `### Comment by userhandle at YYYY-MM-DD HH:MM`.
5. **Decide what draft 2 actually is.** (Draft 3 needs no such decision — it is a
   plain defect with a reproduction, and the least contentious of the three.) It is written as a discussion issue
   asking the maintainer for a scope ruling — and you are the maintainer. If the
   point is to settle the question in public, an issue is right; if it is a
   design note to yourself, `research/reports/reportNNN-*.md` is the better home
   and the issue tracker stays free of questions only you can answer. Draft 1
   does not depend on the answer.

Filing is a human action by genscalator's own rule — the agent prepares, the
human submits (`CONTRIBUTING.md`, "For agents"). The drafts carry the required
agent-disclosure line; keep it if an agent did the work, and remove it honestly
if you rewrite them yourself.

### Ideas not yet done

- A statusline per profile, so the plan is visible inside the session rather
  than only at launch. **Half done:** `config/shared.json` now wires
  genscalator's `tt statusline --mode-line --box-line` (§3), which gives three
  lines of repo, context, mode and box health — but nothing in them says *which
  plan you are on*, which was the point. The remaining piece is a profile chip.
  A fourth main class emitting one (`claude-who --chip`) composes badly, since
  `statusLine` runs a single command; more likely shapes are a wrapper script
  that prefixes the chip to `tt statusline`'s output, or upstream support for a
  caller-supplied chip.
- Upstream the multi-profile machinery into genscalator, so one user with
  several plans is a supported case rather than a local workaround. Both halves are drafted and
  unfiled — see the TODO above. Two tiers, worth separating: (a) the **narrow
  fix** — make genscalator honour
  `CLAUDE_CONFIG_DIR` in the six places it hardcodes `$HOME/.claude` (§3), which
  is a small, self-contained correctness change; (b) the **broad idea** — the
  profile registry, deep merge and launcher in `scala/` as a `tt` tool. (b) is a
  real design question, not a patch: it overlaps the `claude-profile` binary
  that already works, and it would put account-switching inside a toolbox whose
  scope is otherwise text and files.
- `claude-profile doctor` could check token expiry — `.credentials.json` holds
  `expiresAt` — and warn before a session fails mid-task.
- Nothing currently distinguishes the two terminals visually. A different
  `theme` in `config/personal.json` is the one-line version.

---

## 7. Verifying any of this yourself

Everything asserted above came from one of three probes. They are cheap, and
worth repeating when Claude Code updates, since none of this is a stable API.

### Read your own account block

```
python3 -c "import json;print(json.load(open('$HOME/.claude.json'))['oauthAccount'])"
```

`oauthAccount` is where `emailAddress`, `organizationName`, `organizationType`,
`organizationRole`, `seatTier` and `billingType` live — the whole of §1. The
plan name as such is *not* stored; `seatTier` is the closest thing to it.
`~/.claude/.credentials.json` additionally carries `subscriptionType` (`team`
here) and `rateLimitTier`. This is exactly what `claude-who` automates.

### Probe where the config home resolves to

```
D=/tmp/cfgprobe; rm -rf $D; mkdir -p $D
CLAUDE_CONFIG_DIR=$D claude mcp list
ls -a $D          # -> .claude.json was created HERE, not in $HOME
```

Two things make this a valid probe. `claude mcp list` reads and writes config
but needs no sign-in, so it is safe against an empty directory. And `claude
--version` is **not** a valid probe — it returns without initialising config at
all, so it creates nothing and proves nothing. That false negative is easy to
trip over.

### Grep the CLI binary for its own error strings

The binary embeds its diagnostics as plain strings, which is the most direct
statement of intent available:

```
B=$(readlink -f "$(which claude)")
grep -ao ".\{120\}CLAUDE_CONFIG_DIR.\{200\}" "$B" | less
```

That is where the two hard constraints in §3 come from, verbatim:

- *"the configuration home (CLAUDE_CONFIG_DIR) is not an absolute path"*
- *"…where your own settings are found (CLAUDE_CONFIG_DIR, the HOME it defaults
  from, or the cowork settings switch) changed after Claude Code started: set it
  in the shell, not a settings file"*

and two more caveats worth knowing:

- *"ensure the subprocess CLAUDE_CONFIG_DIR matches the parent (same path, same
  separators) or transcript_mirror frames will be dropped"* — anything that
  spawns a nested Claude Code must inherit the same value. The launcher in §5
  sets it on the child's environment, so ordinary subagents inherit it; a custom
  spawner or a container boundary is where this breaks.
- *"Use CLAUDE_CONFIG_DIR=/tmp for ephemeral local writes with external
  mirroring"* — the supported way to get a throwaway config home.

One behaviour is silently switched off by a custom config home: the
`home-settings-seed` path, which seeds settings from `HOME` for managed cloud
workers, refuses to start with reason `config_dir` whenever `CLAUDE_CONFIG_DIR`
is set (it also checks `CLAUDE_CODE_DISABLE_HOME_SETTINGS_SEED`). Irrelevant
locally; relevant if you ever run this profile inside a managed cloud worker.

### Check what a running session actually resolved to

`/status` inside a session reports the active model and account. It is the only
one of these that reflects the merged, running configuration rather than files
on disk.

### Docs note

`docs.claude.com/en/docs/claude-code/*` now 301-redirects to
`code.claude.com/docs/en/*`. Old bookmarks still work; fetchers that do not
follow cross-host redirects will not.

---

## 8. Status of what is here

- **Verified on this machine:** the account/org/seat facts in §1; the config-home
  inventory and that credentials are a plaintext file, not a keychain entry;
  `CLAUDE_CONFIG_DIR` relocating `.claude.json`; the absolute-path and
  set-it-in-the-shell constraints, quoted from the binary; the precedence table
  and merge rules; the model aliases; the Fable-by-plan rules; and the Scala
  sources compiling under Scala 3.9.0 with `list` / `show` / `doctor` /
  `claude-who` producing correct output against the real work profile.
- **Also verified:** plugins are per config home (`genscalator@bjornregnell` is
  installed on personal and absent from work); `DocSyncMain --check` exits 1 on
  drift and 0 when clean, across all five embedded blocks.
- **Also verified:** both native images build and run. `bin/claude-profile` and
  `bin/claude-who` were produced with GraalVM 25.0.2 and gcc 13.3.0 using the
  plain build commands with no workaround flags, start in ~5 ms, and
  `claude-who` reports the real work account correctly. `--no-fallback` needed
  no reflection configuration.
- **Also verified (2026-09-15):** the genscalator statusline runs on the personal
  profile — all three lines — wired from `config/shared.json` as
  `$HOME/.genscalator/bin/tt statusline --mode-line --box-line` and picked up by
  a `claude-profile personal -- --continue` relaunch. The bare `tt` form of the
  same command renders nothing, for the PATH reason in §3.
- **Resolved since first writing:** both profiles are signed in and verified
  end to end. A Max account reports `seatTier: null` and states its tier in
  `organizationRateLimitTier` (`default_claude_max_5x`), which confirms Max 5x
  and is now what `Account.plan` reads.
- **Settled out of band:** the work account is on the scientists plan,
  confirmed by the org's primary owner. Not machine-verifiable — see §1.
- **Fragile by nature:** `seatTier` strings, the `oauthAccount` shape and the
  binary's error strings are internal. They are the best evidence available, but
  they are not an API and may change with any update. §7 is how you re-check.

### Toolchain this was built against

| tool | version |
|---|---|
| Claude Code | 2.1.272 (`~/.local/share/claude/versions/2.1.272`) |
| scala-cli | 1.17.0 |
| Scala | 3.9.0 (scala-cli default) |
| JVM for development | Temurin 21.0.5 |
| GraalVM for packaging | `graalvm-community:25.0.2`, fetched by scala-cli |
| C toolchain for linking | gcc 13.3.0 via `build-essential`, plus `zlib1g-dev` |
| ujson | 4.4.3 |

The three binaries' sources plus `docsync.scala` all compile together from
`scala/`; each `package` run selects its main class. `docsync` is deliberately
*not* built native — it runs rarely, from the repo, via `scala-cli run`.

Dependency versions were picked by asking coursier what actually exists rather
than from memory, which is worth repeating when you bump them:

```
cs complete-dep com.lihaoyi:ujson_3:
cs java --available | grep graalvm-community
```

---

## 9. Gotchas

- **Never set `CLAUDE_CONFIG_DIR=$HOME/.claude`.** §3 explains why.
- **`CLAUDE_CONFIG_DIR` must be absolute** and **set in the shell**, not in a
  settings file.
- **A new profile starts empty** — no theme, no trusted projects, no MCP servers,
  no memory, no plugins. That is the point, but the first run feels bare.
- **Never copy `.credentials.json` between profiles.** Sign in with `/login`.
- **Two plans, two limits.** Work usage counts against the the university org org; personal
  against your Max subscription. Keep private work on the private profile —
  that is the whole reason for the split.
- **Org policy differs.** Work has `allow_remote_control: false` and
  `allow_quick_web_setup: false` from org policy; personal will not inherit
  those, so features may appear on one side and not the other.
- **You are `user`, not admin, in the university org.** You cannot enable extra usage or
  change seat tier yourself — that is an org-admin action.
- **Tell the windows apart.** Give the personal profile a different theme in
  `config/personal.json`, or a statusline, so you never paste work code into the
  private session.
- **`effective-settings.json` is generated.** Edit `config/*.json`, never the
  merged file; it is overwritten on every launch.
- **Credentials are plaintext on Linux.** Two profiles means two token files
  readable by anything running as you. Do not back up or sync a config home
  unencrypted, and do not commit one.
- **A bare `tt` in `statusLine` never works.** The plugin's `bin/` is added to
  the *Bash tool's* PATH, not the `claude` process's, and the statusline is
  spawned from the process. Use `$HOME/.genscalator/bin/tt` — also 8 ms against
  the plugin launcher's 656 ms. A failing statusline renders a blank line, not
  an error; `touch ~/.claude/gs-statusline-dump-on` to find out whether it ran
  at all. §3.
- **genscalator writes its state to `~/.claude` regardless of profile.**
  `gs-modes`, `gs-limits.json`, `gs-sessions`, `tt memory` and the cache root it
  uses to self-update all ignore `CLAUDE_CONFIG_DIR`, so personal-profile state
  lands in the work config home. §3.
- **One CLI, two plans.** The installation is shared; an update to
  `~/.local/share/claude/versions/*` changes both profiles at once. There is no
  per-profile version pin.
- **Nested Claude Code must inherit `CLAUDE_CONFIG_DIR`.** The CLI warns that a
  subprocess with a mismatched value drops transcript-mirror frames. The
  launcher sets it on the child environment so ordinary subagents are fine;
  containers and custom spawners are where this breaks.
- **`claude --version` is not a probe.** It returns without initialising config,
  so it neither creates nor reveals a config home. Use `claude mcp list`.
- **`--settings` takes a file *or* a JSON literal.** The launcher writes a file
  because it is inspectable and avoids quoting hazards, but inline JSON is
  equally valid if you ever want a one-off override.
- **A few security-sensitive keys invert precedence.** For those, Claude Code
  honours the *stricter* value from a lower level over a managed one — so a
  managed setting is not always the winner. See "Exceptions to managed settings
  precedence" in the settings docs.
- **`shell-snapshots/` is not project-scoped.** Project-level cleanup commands
  will not touch it; it accumulates per config home.
- **Syncthing ignore patterns are unanchored by default.** `bin` would match
  `~/asd/bin` — your own scripts — as well as `~/asd/claude/bin`. Anchor with a
  leading `/` whenever the name is not unique to build output.
- **`.stignore` never syncs.** Put the patterns in an included file and
  recreate the one-line `.stignore` on each machine, or the exclusions silently
  apply on one machine only.
- **User-scope plugins do not cross profiles; project and local scope do.**
  `plugins/` lives in the config home, so a user-scope install must be repeated
  on the other plan, marketplace included. Project and local scope live in the
  repository, so they load under whichever profile opens it — see §3.
- **Removing a marketplace uninstalls its plugins**, and only on the profile
  where you remove it.
- **Plugin docs assume `~/.claude`.** Any instruction naming that path — the
  `rm -rf ~/.claude/plugins/cache` fix, for instance — needs translating to
  `~/.claude-personal` on the personal profile.
- **The statusline reaches a session only through `claude-profile`.** The
  `statusLine` key lives in `config/shared.json` and arrives via `--settings`.
  Neither profile's own `settings.json` carries it, so a bare `claude` renders
  no statusline at all. Check how the session was launched before suspecting
  the tool — the symptom is identical to the bare-`tt` failure in §3, and the
  two are easy to confuse.
- **An expired `gh auth` does not block reading a public repository.** Commits,
  the git tree and the open-PR list are all readable anonymously through the
  forge API. Reaching for `gh auth login` when the question is read-only costs
  an interactive round trip for nothing:
  `curl -s https://api.github.com/repos/<owner>/<repo>/pulls?state=open`.
- **`tt --version` is not a thing**, though `reqts/issues/README.md` tells you
  to state the version that way. `tt` has no such subcommand — it answers
  `no such tool '--version'`. Read `VERSION.txt` instead, which is what issue
  064's provenance header does: `v0.10.2 (from VERSION.txt) at <sha>`.
- **Do not pipe the build through `tail` if you check its exit status.** A
  pipeline reports the status of its *last* command, so
  `scala-cli package ... | tail -30` exits `0` even when the build failed —
  which is exactly how the first failed build here looked successful. Check
  `${PIPESTATUS[0]}`, or do not pipe.
- **`native-image` needs a C toolchain at link time**, which GraalVM does not
  bring with it: `build-essential` and `zlib1g-dev`. A green compile phase says
  nothing about the link — failures there are about system packages, not your
  Scala. See the prerequisites in §5.
- **GraalVM insists on `gcc` by name.** Having clang as `/usr/bin/cc` is not
  enough; it aborts before doing any work unless you pass
  `--native-compiler-path` explicitly.

---

## Sources

- [Claude Team plan for scientists — claude.com](https://claude.com/programs/team-plan-for-scientists)
- [Claude Team plan for scientists — Help Center](https://support.claude.com/en/articles/16634237-claude-team-plan-for-scientists)
- [Expanding our support for scientists — Anthropic](https://www.anthropic.com/news/expanding-support-for-scientists)
- [Claude Fable models on your plan — Help Center](https://support.claude.com/en/articles/15424964-claude-fable-models-on-your-plan)
- [How large is the context window on paid Claude plans — Help Center](https://support.claude.com/en/articles/8606394-how-large-is-the-context-window-on-paid-claude-plans)
- [Settings files and precedence — Claude Code docs](https://code.claude.com/docs/en/settings)
- [Model configuration — Claude Code docs](https://code.claude.com/docs/en/model-config)
- [Environment variables — Claude Code docs](https://code.claude.com/docs/en/env-vars)
- [Discover and install plugins — Claude Code docs](https://code.claude.com/docs/en/discover-plugins)
- [Create plugins — Claude Code docs](https://code.claude.com/docs/en/plugins)
- [Syncthing: ignoring files](https://docs.syncthing.net/users/ignoring.html)
- [scala-cli: package command](https://scala-cli.virtuslab.org/docs/commands/package/)
- [scala-cli: using directives reference](https://scala-cli.virtuslab.org/docs/reference/directives)
