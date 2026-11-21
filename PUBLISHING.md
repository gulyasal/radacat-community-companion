# Your first GitHub publication

Nothing in these instructions uploads files automatically. Review the clean folder before making it public. Do not upload the parent development workspace.

## 1. Create a repository

Sign in to your `gulyasal` account at https://github.com/new and use:

- Repository name: `radacat-community-companion`
- Description: `Unofficial Android and Windows companions restoring public text messaging for Radacat C1 devices.`
- Visibility: Public, when you are ready to share it.

For browser uploads, a GitHub-generated README is optional; our README will replace it. Do not add a conflicting license: this folder already contains MIT.

## 2. Upload the clean source folder

In the repository, choose **Add file → Upload files**. Drag the CONTENTS of the prepared `radacat-community-companion` folder into the upload area. You should see `README.md`, `LICENSE`, `android`, `desktop` and `assets` at repository root, not an extra enclosing folder. Commit with a description such as `Initial community preview`.

Include `.gitignore` if your file picker hides dotfiles. Never drag the original `D:\Radacat` workspace into GitHub: it contains private analysis material, backups and signing keys. Do not upload `build`, `tools`, `.venv`, logs, local settings or any keystore.

The source ZIP is a convenient local backup. Upload the extracted source contents to the repository; attaching only a ZIP does not create a browsable code repository.

## 3. Create an early preview release

Open **Releases → Draft a new release**:

- Tag: `v0.1.0-preview`
- Title: `First community preview — C1 public messaging`
- Paste the supplied `RELEASE_NOTES.md` text.
- Attach `Radacat-Community-Companion-preview.apk`, `Radacat-Community-Desktop-preview.zip`, `Radacat-Community-Source-preview.zip` and `SHA256SUMS.txt` from the sibling `release-assets` folder. The Windows ZIP contains the standalone EXE; the separately supplied EXE is an optional additional download.
- Mark it as a pre-release, save as a draft, review, then publish.

The preview tag is a project-wide release label; individual app versions may differ. The new community signing key is separate from the owner's old private test key. Do not share keys/password files; keep an offline backup of the release key for updates.

## 4. Share the repository link

The resulting address will be `https://github.com/gulyasal/radacat-community-companion`. Other C1 owners can read the README and download release files without giving us their passwords or Radacat accounts.

Official help:

- Upload files: https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository
- Create releases: https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository
- Licenses: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository
