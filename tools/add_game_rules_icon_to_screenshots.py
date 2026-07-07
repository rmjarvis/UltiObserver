"""Add the live-game rules icon to older documentation screenshots.

This is a small image-repair helper for screenshots captured before the rules icon existed in
the cap-status row. It preserves the existing screenshot content, shifts the cap text left just
enough to make room, and pastes the icon from a current template screenshot.
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from PIL import Image, ImageDraw


REPO_ROOT = Path(__file__).resolve().parents[1]

DEFAULT_TEMPLATES = {
    "live": Path("/Users/Mike/Downloads/Screenshot 2026-07-07 at 9.07.10\u202fAM.png"),
    "locked": Path("/Users/Mike/Downloads/Screenshot 2026-07-07 at 9.07.34\u202fAM.png"),
    "dim": Path("/Users/Mike/Downloads/Screenshot 2026-07-07 at 9.07.54\u202fAM.png"),
}

DEFAULT_TARGETS = [
    ("docs/screen-shots/OffenseSignalTimer.png", "live", "normal"),
    ("docs/screen-shots/LockedScreen.png", "locked", "normal"),
    ("docs/screen-shots/TimeoutCountdown.png", "live", "normal"),
    ("docs/screen-shots/Offsides.png", "dim", "normal"),
    ("docs/screen-shots/TimeViolation.png", "dim", "normal"),
    ("docs/screen-shots/ThirdCardPenalty.png", "dim", "normal"),
    ("docs/screen-shots/YellowCardPlayer.png", "dim", "obscured"),
    ("docs/screen-shots/YellowCardReason.png", "dim", "obscured"),
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "targets",
        nargs="*",
        help="Screenshots to update. Defaults to the currently published active-game images.",
    )
    parser.add_argument(
        "--mode",
        choices=("live", "locked", "dim"),
        help="Template mode to use for all explicit targets.",
    )
    parser.add_argument(
        "--template-live",
        type=Path,
        default=DEFAULT_TEMPLATES["live"],
        help="Current live-game screenshot to copy the normal icon from.",
    )
    parser.add_argument(
        "--template-locked",
        type=Path,
        default=DEFAULT_TEMPLATES["locked"],
        help="Current locked-game screenshot to copy the disabled icon from.",
    )
    parser.add_argument(
        "--template-dim",
        type=Path,
        default=DEFAULT_TEMPLATES["dim"],
        help="Current dimmed-dialog screenshot to copy the dimmed icon from.",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Update screenshots even when an existing icon is detected.",
    )
    parser.add_argument(
        "--obscured-dialog",
        action="store_true",
        help=(
            "For explicit targets where a dialog covers most of the cap row, erase only the "
            "visible old cap-timer sliver and paste the icon."
        ),
    )
    parser.add_argument(
        "--no-backup",
        action="store_true",
        help="Do not write a .bak file before modifying a screenshot.",
    )
    return parser.parse_args()


def image_path(path: str | Path) -> Path:
    path = Path(path)
    return path if path.is_absolute() else REPO_ROOT / path


def changed_pixel_bbox(
    image: Image.Image,
    region: tuple[int, int, int, int],
    background_sample: tuple[int, int],
    threshold: int,
) -> tuple[int, int, int, int] | None:
    """Return the bounding box of pixels that differ from the sampled background color."""

    background = image.getpixel(background_sample)[:3]
    pixels = image.load()
    x0, y0, x1, y1 = region
    xs: list[int] = []
    ys: list[int] = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            red, green, blue = pixels[x, y][:3]
            distance = (
                abs(red - background[0])
                + abs(green - background[1])
                + abs(blue - background[2])
            )
            if distance > threshold:
                xs.append(x)
                ys.append(y)

    if not xs:
        return None
    return min(xs), min(ys), max(xs) + 1, max(ys) + 1


def expand_box(
    box: tuple[int, int, int, int],
    dx: int,
    dy: int,
    image_size: tuple[int, int],
) -> tuple[int, int, int, int]:
    x0, y0, x1, y1 = box
    width, height = image_size
    return (
        max(0, x0 - dx),
        max(0, y0 - dy),
        min(width, x1 + dx),
        min(height, y1 + dy),
    )


def icon_box(template: Image.Image) -> tuple[int, int, int, int]:
    """Find the rules icon in a current screenshot template."""

    box = changed_pixel_bbox(
        template,
        region=(646, 145, 690, 205),
        background_sample=(640, 150),
        threshold=28,
    )
    if box is None:
        raise RuntimeError("Could not find the rules icon in the template screenshot.")
    return expand_box(box, dx=4, dy=4, image_size=template.size)


def target_has_icon(image: Image.Image) -> bool:
    """Return whether the screenshot already appears to have the rules icon."""

    box = changed_pixel_bbox(
        image,
        region=(646, 145, 690, 205),
        background_sample=(640, 150),
        threshold=28,
    )
    if box is None:
        return False

    x0, y0, x1, y1 = box
    return x1 - x0 <= 32 and y1 - y0 <= 34


def cap_text_box(image: Image.Image) -> tuple[int, int, int, int]:
    """Find the cap label/countdown text in an old screenshot."""

    box = changed_pixel_bbox(
        image,
        region=(300, 125, 690, 215),
        background_sample=(350, 135),
        threshold=45,
    )
    if box is None:
        raise RuntimeError("Could not find the cap text in the target screenshot.")
    return expand_box(box, dx=3, dy=5, image_size=image.size)


def update_screenshot(
    path: Path,
    mode: str,
    style: str,
    templates: dict[str, Image.Image],
    force: bool,
    write_backup: bool,
) -> bool:
    image = Image.open(path).convert("RGB")
    if target_has_icon(image) and not force:
        print(f"skip {path}: rules icon already detected")
        return False

    icon_bounds = icon_box(templates[mode])
    if style == "obscured":
        return update_obscured_screenshot(
            image=image,
            path=path,
            icon_crop=templates[mode].crop(icon_bounds),
            icon_left=icon_bounds[0],
            force=force,
            write_backup=write_backup,
        )

    text_box = cap_text_box(image)

    # Mike liked the final hand-tuned position one pixel above the current template crop.
    icon_left = icon_bounds[0]
    icon_top = icon_bounds[1] - 7
    desired_text_right = icon_left - 16
    text_shift = max(0, text_box[2] - desired_text_right)
    shifted_text_box = (
        text_box[0] - text_shift,
        text_box[1],
        text_box[2] - text_shift,
        text_box[3],
    )

    text_crop = image.crop(text_box)
    icon_crop = templates[mode].crop(icon_bounds)
    icon_destination = (
        icon_left,
        icon_top,
        icon_left + icon_crop.width,
        icon_top + icon_crop.height,
    )
    erase_box = (
        min(text_box[0], shifted_text_box[0], icon_destination[0]) - 2,
        min(text_box[1], shifted_text_box[1], icon_destination[1]) - 2,
        max(text_box[2], shifted_text_box[2], icon_destination[2]) + 2,
        max(text_box[3], shifted_text_box[3], icon_destination[3]) + 2,
    )

    background = image.getpixel((350, 135))[:3]
    ImageDraw.Draw(image).rectangle(erase_box, fill=background)
    image.paste(text_crop, shifted_text_box[:2])
    image.paste(icon_crop, icon_destination[:2])

    if write_backup:
        backup = path.with_suffix(path.suffix + ".bak")
        shutil.copy2(path, backup)

    image.save(path)
    print(f"updated {path}")
    return True


def update_obscured_screenshot(
    image: Image.Image,
    path: Path,
    icon_crop: Image.Image,
    icon_left: int,
    force: bool,
    write_backup: bool,
) -> bool:
    """Patch a screenshot where a dialog hides most of the cap-status row."""

    if target_has_icon(image) and not force:
        print(f"skip {path}: rules icon already detected")
        return False

    icon_top = 151
    icon_destination = (
        icon_left,
        icon_top,
        icon_left + icon_crop.width,
        icon_top + icon_crop.height,
    )
    background = image.getpixel((640, 150))[:3]

    # The current card-entry dialogs cover the shifted cap text, leaving only the icon slot
    # visible. Erase the old right-edge countdown sliver without touching the dialog edge.
    ImageDraw.Draw(image).rectangle((629, 145, 690, 195), fill=background)
    image.paste(icon_crop, icon_destination[:2])

    if write_backup:
        backup = path.with_suffix(path.suffix + ".bak")
        shutil.copy2(path, backup)

    image.save(path)
    print(f"updated {path}")
    return True


def main() -> None:
    args = parse_args()
    templates = {
        "live": Image.open(args.template_live).convert("RGB"),
        "locked": Image.open(args.template_locked).convert("RGB"),
        "dim": Image.open(args.template_dim).convert("RGB"),
    }

    if args.targets:
        if args.mode is None:
            raise SystemExit("--mode is required when explicit targets are passed.")
        style = "obscured" if args.obscured_dialog else "normal"
        targets = [(target, args.mode, style) for target in args.targets]
    else:
        targets = DEFAULT_TARGETS

    for target, mode, style in targets:
        update_screenshot(
            path=image_path(target),
            mode=mode,
            style=style,
            templates=templates,
            force=args.force,
            write_backup=not args.no_backup,
        )


if __name__ == "__main__":
    main()
