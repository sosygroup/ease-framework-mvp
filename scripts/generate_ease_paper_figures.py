from __future__ import annotations

import argparse
import json
from pathlib import Path

from reportlab.lib.colors import Color, HexColor, white
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf"
OUTPUT.mkdir(parents=True, exist_ok=True)

GREEN = HexColor("#006B4F")
DARK_GREEN = HexColor("#004D39")
PALE_GREEN = HexColor("#E6F2EE")
TEAL = HexColor("#147D86")
PALE_TEAL = HexColor("#E8F4F5")
NAVY = HexColor("#203A5F")
PALE_BLUE = HexColor("#EAF0F7")
AMBER = HexColor("#C98319")
PALE_AMBER = HexColor("#FFF3DE")
RED = HexColor("#A63D40")
PALE_RED = HexColor("#F8E8E8")
INK = HexColor("#17242F")
MUTED = HexColor("#586773")
LINE = HexColor("#9AAAB4")
GRID = HexColor("#D9E1E5")


def rounded_box(
    pdf: canvas.Canvas,
    x: float,
    y: float,
    width: float,
    height: float,
    fill: Color,
    stroke: Color,
    title: str,
    subtitle: str = "",
    title_size: float = 10,
    subtitle_size: float = 7.6,
) -> None:
    pdf.setFillColor(fill)
    pdf.setStrokeColor(stroke)
    pdf.setLineWidth(1.1)
    pdf.roundRect(x, y, width, height, 8, fill=1, stroke=1)
    pdf.setFillColor(INK)
    pdf.setFont("Helvetica-Bold", title_size)
    pdf.drawCentredString(x + width / 2, y + height - 16, title)
    if subtitle:
        pdf.setFillColor(MUTED)
        pdf.setFont("Helvetica", subtitle_size)
        draw_wrapped_centred(
            pdf,
            subtitle,
            x + 8,
            y + 8,
            width - 16,
            height - 28,
            subtitle_size,
            subtitle_size + 2,
        )


def draw_wrapped_centred(
    pdf: canvas.Canvas,
    text: str,
    x: float,
    y: float,
    width: float,
    height: float,
    font_size: float,
    leading: float,
) -> None:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = word if not current else f"{current} {word}"
        if stringWidth(candidate, "Helvetica", font_size) <= width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    total = len(lines) * leading
    baseline = y + max(0, (height + total) / 2) - leading
    pdf.setFont("Helvetica", font_size)
    for line in lines:
        pdf.drawCentredString(x + width / 2, baseline, line)
        baseline -= leading


def arrow(
    pdf: canvas.Canvas,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
    color: Color = NAVY,
    width: float = 1.6,
    head: float = 5.5,
) -> None:
    import math

    pdf.setStrokeColor(color)
    pdf.setFillColor(color)
    pdf.setLineWidth(width)
    pdf.line(x1, y1, x2, y2)
    angle = math.atan2(y2 - y1, x2 - x1)
    left = angle + math.pi * 0.82
    right = angle - math.pi * 0.82
    path = pdf.beginPath()
    path.moveTo(x2, y2)
    path.lineTo(x2 + head * math.cos(left), y2 + head * math.sin(left))
    path.lineTo(x2 + head * math.cos(right), y2 + head * math.sin(right))
    path.close()
    pdf.drawPath(path, fill=1, stroke=0)


def double_arrow(
    pdf: canvas.Canvas,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
    color: Color = TEAL,
) -> None:
    arrow(pdf, x1, y1, x2, y2, color=color, width=1.25, head=4.5)
    arrow(pdf, x2, y2, x1, y1, color=color, width=1.25, head=4.5)


def polyline_double_arrow(
    pdf: canvas.Canvas,
    points: list[tuple[float, float]],
    color: Color = TEAL,
    width: float = 1.25,
    head: float = 4.5,
) -> None:
    """Draw a bidirectional orthogonal connector without crossing component boxes."""
    import math

    if len(points) < 2:
        raise ValueError("A connector requires at least two points")

    pdf.setStrokeColor(color)
    pdf.setFillColor(color)
    pdf.setLineWidth(width)
    path = pdf.beginPath()
    path.moveTo(*points[0])
    for point in points[1:]:
        path.lineTo(*point)
    pdf.drawPath(path, fill=0, stroke=1)

    def draw_head(tip: tuple[float, float], previous: tuple[float, float]) -> None:
        angle = math.atan2(tip[1] - previous[1], tip[0] - previous[0])
        left = angle + math.pi * 0.82
        right = angle - math.pi * 0.82
        arrow_path = pdf.beginPath()
        arrow_path.moveTo(*tip)
        arrow_path.lineTo(tip[0] + head * math.cos(left), tip[1] + head * math.sin(left))
        arrow_path.lineTo(tip[0] + head * math.cos(right), tip[1] + head * math.sin(right))
        arrow_path.close()
        pdf.drawPath(arrow_path, fill=1, stroke=0)

    draw_head(points[-1], points[-2])
    draw_head(points[0], points[1])


def architecture_figure() -> None:
    path = OUTPUT / "ease-mvp-architecture.pdf"
    width, height = 720, 470
    pdf = canvas.Canvas(str(path), pagesize=(width, height), pageCompression=1)
    pdf.setTitle("Executable EASE MVP architecture")
    pdf.setAuthor("EASE Framework authors")

    pdf.setFillColor(INK)
    pdf.setFont("Helvetica-Bold", 16)
    pdf.drawString(28, 444, "Executable EASE MVP architecture")
    pdf.setFillColor(MUTED)
    pdf.setFont("Helvetica", 8.5)
    pdf.drawRightString(
        width - 28,
        446,
        "MAPE-K governance with explicit BDI deliberation",
    )

    # Main MAPE-K flow.
    component_y = 292
    box_w, box_h, gap = 112, 72, 18
    start_x = 78
    labels = [
        ("MONITOR", "validated evidence, provenance, confidence", PALE_BLUE, NAVY),
        ("ANALYSE", "hard violations, mismatch M(t), confidence Q(t)", PALE_TEAL, TEAL),
        ("PLAN", "governance region and candidate autonomy modes", PALE_GREEN, GREEN),
        ("EXECUTE", "confirmation, enactment, feedback, rollback", PALE_AMBER, AMBER),
    ]
    boxes: list[tuple[float, float, float, float]] = []
    for index, (title, subtitle, fill, stroke) in enumerate(labels):
        x = start_x + index * (box_w + gap)
        rounded_box(pdf, x, component_y, box_w, box_h, fill, stroke, title, subtitle)
        boxes.append((x, component_y, box_w, box_h))
        if index:
            previous = boxes[index - 1]
            arrow(
                pdf,
                previous[0] + previous[2],
                component_y + box_h / 2,
                x,
                component_y + box_h / 2,
            )

    # Evidence and managed system.
    rounded_box(
        pdf,
        20,
        component_y + 6,
        48,
        60,
        PALE_BLUE,
        NAVY,
        "INPUT",
        "sensors and stakeholder interaction",
        8.5,
        6.5,
    )
    arrow(pdf, 68, component_y + 36, start_x, component_y + 36)
    managed_x = boxes[-1][0] + box_w + 10
    rounded_box(
        pdf,
        managed_x,
        component_y + 6,
        90,
        60,
        PALE_AMBER,
        AMBER,
        "HOME HUB",
        "managed system and autonomy mode",
        8.7,
        6.8,
    )
    arrow(
        pdf,
        boxes[-1][0] + box_w,
        component_y + 36,
        managed_x,
        component_y + 36,
    )

    # BDI detail is aligned below PLAN and connected through shared knowledge.
    bdi_x, bdi_y, bdi_w, bdi_h = 260, 112, 335, 74
    pdf.setFillColor(HexColor("#F5FAF8"))
    pdf.setStrokeColor(GREEN)
    pdf.setLineWidth(1.3)
    pdf.roundRect(bdi_x, bdi_y, bdi_w, bdi_h, 9, fill=1, stroke=1)
    pdf.setFillColor(DARK_GREEN)
    pdf.setFont("Helvetica-Bold", 9.5)
    pdf.drawString(bdi_x + 10, bdi_y + bdi_h - 15, "BDI deliberation inside PLAN")
    bdi_labels = [
        ("BELIEFS", "evidence + context"),
        ("DESIRES", "admissible objectives"),
        ("INTENTIONS", "plan + autonomy mode"),
        ("SELECT", "least intrusive sufficient"),
    ]
    small_w, small_h = 72, 38
    small_y = bdi_y + 10
    for index, (title, subtitle) in enumerate(bdi_labels):
        x = bdi_x + 10 + index * 80
        rounded_box(
            pdf,
            x,
            small_y,
            small_w,
            small_h,
            white,
            GREEN,
            title,
            subtitle,
            7.4,
            5.8,
        )
        if index:
            arrow(pdf, x - 8, small_y + small_h / 2, x, small_y + small_h / 2, GREEN, 1.1, 4)
    # Shared knowledge.
    knowledge_x, knowledge_y, knowledge_w, knowledge_h = 112, 215, 496, 52
    rounded_box(
        pdf,
        knowledge_x,
        knowledge_y,
        knowledge_w,
        knowledge_h,
        PALE_TEAL,
        TEAL,
        "VERSIONED RUNTIME KNOWLEDGE (K)",
        "configuration, evidence, beliefs, desires, intentions, thresholds, traces, contestations, execution records",
        9.5,
        7.0,
    )
    # Knowledge sits directly below MAPE, so every read/write connector is
    # short, vertical, and free from intervening boxes.
    for x, _, w, _ in boxes:
        double_arrow(
            pdf,
            x + w / 2,
            component_y,
            x + w / 2,
            knowledge_y + knowledge_h,
        )
    double_arrow(
        pdf,
        boxes[2][0] + boxes[2][2] / 2,
        knowledge_y,
        boxes[2][0] + boxes[2][2] / 2,
        bdi_y + bdi_h,
        GREEN,
    )

    # External governed components.
    rounded_box(
        pdf,
        20,
        112,
        190,
        74,
        PALE_GREEN,
        GREEN,
        "VALIDATED JSON CONFIGURATION",
        "thresholds, weights, evaluator, constraints, plans, BDI defaults, contestation policy",
        8.7,
        7.0,
    )
    arrow(pdf, 210, 149, bdi_x, 149, GREEN)
    polyline_double_arrow(
        pdf,
        [(115, 186), (115, 200), (225, 200), (225, knowledge_y)],
        GREEN,
    )

    rounded_box(
        pdf,
        20,
        24,
        224,
        64,
        PALE_BLUE,
        NAVY,
        "OPTIONAL LLM COGNITIVE UPDATER",
        "proposal-only belief, desire, and plan assessments; deterministic policy remains authoritative",
        8.7,
        6.9,
    )
    polyline_double_arrow(
        pdf,
        [(20, 56), (10, 56), (10, knowledge_y + knowledge_h / 2),
         (knowledge_x, knowledge_y + knowledge_h / 2)],
        NAVY,
    )

    rounded_box(
        pdf,
        476,
        24,
        224,
        64,
        PALE_RED,
        RED,
        "HUMAN CONTROL AND CONTESTATION",
        "confirmation; historical correction with recomputation; prospective consent change; append-only revision",
        8.7,
        6.9,
    )
    double_arrow(pdf, 603, 88, 603, knowledge_y, RED)
    arrow(pdf, managed_x + 45, component_y + 6, managed_x + 45, 88, RED)

    # Execution feedback loop.
    pdf.setStrokeColor(MUTED)
    pdf.setLineWidth(1.0)
    pdf.setDash(3, 2)
    feedback_y = 382
    pdf.line(managed_x + 45, component_y + 66, managed_x + 45, feedback_y)
    pdf.line(managed_x + 45, feedback_y, start_x + box_w / 2, feedback_y)
    pdf.setDash()
    arrow(pdf, start_x + box_w / 2, feedback_y, start_x + box_w / 2, component_y + box_h, MUTED, 1.0, 4)
    pdf.setFillColor(MUTED)
    pdf.setFont("Helvetica-Oblique", 7)
    pdf.drawCentredString(360, feedback_y + 5, "observed effects and stakeholder feedback start the next cycle")

    pdf.save()


def scenario_figure() -> None:
    batch_files = sorted((ROOT / "output" / "batch").glob("homehub-comparison-*.json"))
    if not batch_files:
        raise FileNotFoundError("No batch result found")
    batch = json.loads(batch_files[-1].read_text(encoding="utf-8"))
    successful = [row for row in batch["results"] if row["status"] == "SUCCESS"]

    path = OUTPUT / "ease-mvp-scenario-comparison.pdf"
    width, height = 720, 430
    pdf = canvas.Canvas(str(path), pagesize=(width, height), pageCompression=1)
    pdf.setTitle("EASE MVP scenario comparison")
    pdf.setAuthor("EASE Framework authors")

    pdf.setFillColor(INK)
    pdf.setFont("Helvetica-Bold", 16)
    pdf.drawString(30, 403, "Configurable scenario outcomes")
    pdf.setFillColor(MUTED)
    pdf.setFont("Helvetica", 8)
    pdf.drawRightString(width - 30, 405, "same engine and result schema for UI, CLI, JSON, and CSV")

    chart_x, chart_y = 74, 132
    chart_w, chart_h = 610, 235
    pdf.setStrokeColor(GRID)
    pdf.setLineWidth(0.7)
    for step in range(0, 11, 2):
        value = step / 10
        y = chart_y + value * chart_h
        pdf.line(chart_x, y, chart_x + chart_w, y)
        pdf.setFillColor(MUTED)
        pdf.setFont("Helvetica", 7)
        pdf.drawRightString(chart_x - 7, y - 2.5, f"{value:.1f}")

    group_w = chart_w / len(successful)
    bar_w = 31
    for index, row in enumerate(successful):
        metrics = row["finalMetrics"]
        mismatch = float(metrics["aggregateMismatch"])
        confidence = float(metrics["aggregateConfidence"])
        centre = chart_x + group_w * (index + 0.5)
        values = [
            (mismatch, GREEN, "M"),
            (confidence, NAVY, "Q"),
        ]
        for offset_index, (value, color, label) in enumerate(values):
            x = centre - bar_w - 3 + offset_index * (bar_w + 6)
            height_value = value * chart_h
            pdf.setFillColor(color)
            pdf.setStrokeColor(color)
            pdf.roundRect(x, chart_y, bar_w, height_value, 3, fill=1, stroke=0)
            pdf.setFillColor(INK)
            pdf.setFont("Helvetica-Bold", 7.2)
            pdf.drawCentredString(x + bar_w / 2, chart_y + height_value + 5, f"{value:.3f}")
            pdf.setFillColor(white)
            pdf.setFont("Helvetica-Bold", 8)
            if height_value > 18:
                pdf.drawCentredString(x + bar_w / 2, chart_y + 6, label)

        scenario_name = row["scenarioName"]
        short_names = {
            "Worked example": "Worked\nexample",
            "Advisory-first plan calibration": "Advisory-first\ncalibration",
            "Conservative threshold policy": "Conservative\ngovernance",
            "Worked example after historical evidence correction": "Historical\ncorrection",
        }
        lines = short_names.get(scenario_name, scenario_name).split("\n")
        pdf.setFillColor(INK)
        pdf.setFont("Helvetica-Bold", 7.5)
        baseline = chart_y - 16
        for line in lines:
            pdf.drawCentredString(centre, baseline, line)
            baseline -= 9
        pdf.setFillColor(MUTED)
        pdf.setFont("Helvetica", 6.8)
        pdf.drawCentredString(
            centre,
            baseline - 1,
            f"{row['finalPlanId']} / {row['finalMode'].title()}",
        )

    pdf.setStrokeColor(INK)
    pdf.setLineWidth(1)
    pdf.line(chart_x, chart_y, chart_x + chart_w, chart_y)
    pdf.line(chart_x, chart_y, chart_x, chart_y + chart_h)
    pdf.saveState()
    pdf.translate(20, chart_y + chart_h / 2)
    pdf.rotate(90)
    pdf.setFillColor(MUTED)
    pdf.setFont("Helvetica-Bold", 8)
    pdf.drawCentredString(0, 0, "Normalised value")
    pdf.restoreState()

    legend_y = 58
    for x, color, label in [
        (75, GREEN, "Aggregate mismatch M(t)"),
        (230, NAVY, "Aggregate confidence Q(t)"),
    ]:
        pdf.setFillColor(color)
        pdf.roundRect(x, legend_y, 11, 11, 2, fill=1, stroke=0)
        pdf.setFillColor(INK)
        pdf.setFont("Helvetica", 7.6)
        pdf.drawString(x + 16, legend_y + 2, label)

    failed = int(batch.get("failed", 0))
    succeeded = int(batch.get("succeeded", len(successful)))
    pdf.setFillColor(MUTED)
    pdf.setFont("Helvetica-Oblique", 7.2)
    pdf.drawRightString(
        width - 35,
        legend_y + 2,
        f"Batch completed: {succeeded} successful scenarios; {failed} invalid scenario isolated as an error.",
    )
    pdf.save()


def main() -> None:
    global OUTPUT

    parser = argparse.ArgumentParser(
        description="Generate the vector figures used by the EASE MVP paper."
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=ROOT / "output" / "pdf",
        help="Destination directory (default: output/pdf).",
    )
    parser.add_argument(
        "--figure",
        choices=("all", "architecture", "scenario"),
        default="all",
        help="Generate all figures or only one selected figure.",
    )
    args = parser.parse_args()

    OUTPUT = args.output_dir
    if not OUTPUT.is_absolute():
        OUTPUT = ROOT / OUTPUT
    OUTPUT.mkdir(parents=True, exist_ok=True)

    if args.figure in ("all", "architecture"):
        architecture_figure()
        print(OUTPUT / "ease-mvp-architecture.pdf")
    if args.figure in ("all", "scenario"):
        scenario_figure()
        print(OUTPUT / "ease-mvp-scenario-comparison.pdf")


if __name__ == "__main__":
    main()
