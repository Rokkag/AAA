# Pawchive — Mihon/Tachiyomi Extension Repo

A custom extension repo for [Mihon](https://mihon.app) / Tachiyomi that adds **Pawchive**
as a source (kemono-style creator-archive site).

---

## ➕ Adding to Mihon (no building required)

1. Push this repo to GitHub (see below)
2. Wait ~3 minutes for the **Actions** tab to show a green ✅
3. In **Mihon → Settings → Browse → Extension repos**, tap **+** and paste:
   ```
   https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO_NAME/repo/index.json
   ```
   *(replace `YOUR_USERNAME` and `YOUR_REPO_NAME` with your actual values)*
4. Go to **Browse → Extensions** — tap the cloud icon next to **Pawchive** to install
5. Enable **"Show NSFW sources"** in Settings → Browse (the extension is flagged NSFW)

---

## 🚀 First-time GitHub setup

You only need to do this once:

1. **Create a GitHub account** if you don't have one → https://github.com/signup
2. **Create a new repository** → https://github.com/new
   - Name it anything (e.g. `pawchive-extension`)
   - Set to **Public** (required for Mihon to fetch the raw files)
   - Don't add a README or .gitignore — keep it empty
3. **Upload the files** — easiest way is the GitHub web UI:
   - Open your new repo → click **"uploading an existing file"**
   - Drag in everything from this zip (keeping the folder structure)
   - Commit directly to `main`
4. GitHub Actions runs automatically — watch progress in the **Actions** tab
5. Once it's green, your repo URL is ready to add to Mihon

---

## Conceptual mapping

| Pawchive | Mihon |
|---|---|
| Creator profile | Manga / Series |
| Post | Chapter |
| Attached image | Page |

## Features

- Browse creators by last-updated or popularity
- Latest updates feed
- Search by name, platform (Patreon, Fanbox, …), tag
- Full post history with auto-pagination
- Cloudflare bypass + rate limiting built in
- Deep-link from browser → reader
