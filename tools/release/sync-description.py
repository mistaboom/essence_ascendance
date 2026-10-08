"""Publish the canonical README without changing releases or other project fields."""

import argparse
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parents[2]
USER_AGENT = "mistaboom/essence_ascendance (project description sync)"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward a publishing credential to a redirected destination.
        return None


def request(platform, description, token):
    if platform == "modrinth":
        return urllib.request.Request(
            "https://api.modrinth.com/v2/project/zIJXnoE8",
            data=json.dumps({"body": description}, ensure_ascii=False).encode("utf-8"),
            headers={"Authorization": token, "Content-Type": "application/json",
                     "User-Agent": USER_AGENT},
            method="PATCH",
        )

    boundary = "essence-description-" + uuid.uuid4().hex
    metadata = json.dumps({"description": description, "descriptionType": "markdown"},
                          ensure_ascii=False)
    body = (f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="metadata"\r\n'
            "Content-Type: application/json; charset=utf-8\r\n\r\n"
            f"{metadata}\r\n--{boundary}--\r\n").encode("utf-8")
    return urllib.request.Request(
        "https://minecraft.curseforge.com/api/projects/1732850/update-project",
        data=body,
        headers={"X-Api-Token": token, "User-Agent": USER_AGENT,
                 "Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )


def sync(platform, description, token):
    if not description.strip():
        raise ValueError("README.md is empty; refusing to replace the listing.")
    if not token or not token.strip():
        raise ValueError(f"Missing {platform} repository secret.")
    opener = urllib.request.build_opener(NoRedirect())
    with opener.open(request(platform, description, token.strip()), timeout=30) as response:
        # Consume the response, but do not log response bodies or request headers.
        response.read()

    if platform == "modrinth":
        verification = urllib.request.Request(
            "https://api.modrinth.com/v2/project/zIJXnoE8",
            headers={"Authorization": token.strip(), "User-Agent": USER_AGENT},
        )
        with opener.open(verification, timeout=30) as response:
            current = json.load(response)
        if current.get("body") != description:
            raise ValueError("Modrinth's saved description does not match README.md.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=("modrinth", "curseforge"), required=True)
    args = parser.parse_args()
    description = (ROOT / "README.md").read_text(encoding="utf-8")
    try:
        sync(args.platform, description, os.environ.get("PLATFORM_TOKEN", ""))
    except urllib.error.HTTPError as error:
        print(f"{args.platform}: HTTP {error.code}. Check the token and project access.",
              file=sys.stderr)
        return 1
    except urllib.error.URLError:
        print(f"{args.platform}: connection failed. Check the listing before rerunning.",
              file=sys.stderr)
        return 1
    except (ValueError, TimeoutError, OSError):
        print(f"{args.platform}: sync failed. Check README.md, the secret, and the listing.",
              file=sys.stderr)
        return 1
    print(f"{args.platform}: README.md description synchronized.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
