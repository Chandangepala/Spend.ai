"""Trains SpendAI's on-device category model and writes it to the app's resources.

The model is a linear classifier over hashed word and letter-fragment features. It is small
(~460 KB), runs in plain Kotlin (no ML library) and knows nothing about any user: it learns only
from data/seed.txt. Feature extraction here must match CategoryModel.kt exactly; the app's unit
test re-scores data/eval.txt in Kotlin and checks it gets the same result as this script.

    python3 -m venv .venv && .venv/bin/pip install numpy scipy scikit-learn
    .venv/bin/python train.py
"""
import random
import re
import struct
import sys
from pathlib import Path

import numpy as np
from scipy.sparse import csr_matrix
from sklearn.linear_model import LogisticRegression

HERE = Path(__file__).parent
OUT = HERE.parent.parent / "app/src/main/resources/category_model.bin"
DIM = 1 << 15
MAGIC = b"SPCM"
VERSION = 1


def read_phrases(path):
    """[(category, phrase)] from the bracketed, comma-separated format of seed.txt / eval.txt."""
    rows, category = [], None
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        header = re.fullmatch(r"\[(\w+)\]", line)
        if header:
            category = header.group(1)
            continue
        rows += [(category, p.strip()) for p in line.split(",") if p.strip()]
    return rows


def normalize(text):
    """Lowercase letters only: digits and symbols become spaces ("PAYTM*BLINKIT4412" -> "paytm blinkit")."""
    return " ".join("".join(c if c.isalpha() else " " for c in text.lower()).split())


def features(text):
    words = normalize(text).split(" ") if normalize(text) else []
    out = [f"w:{w}" for w in words]
    out += [f"b:{a} {b}" for a, b in zip(words, words[1:])]
    for w in words:
        padded = f"^{w}$"
        for n in (3, 4):
            out += [f"c:{padded[i:i + n]}" for i in range(len(padded) - n + 1)]
    return out


def fnv1a(s):
    h = 0x811C9DC5
    for byte in s.encode("utf-8"):
        h ^= byte
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def vectorize(texts):
    rows, cols, vals = [], [], []
    for r, text in enumerate(texts):
        counts = {}
        for f in features(text):
            j = fnv1a(f) % DIM
            counts[j] = counts.get(j, 0) + 1.0
        norm = sum(v * v for v in counts.values()) ** 0.5 or 1.0
        for j, v in counts.items():
            rows.append(r)
            cols.append(j)
            vals.append(v / norm)
    return csr_matrix((vals, (rows, cols)), shape=(len(texts), DIM), dtype=np.float64)


# Words that appear around any merchant in bank SMS and UPI names; added to training phrases so the
# model learns to ignore them rather than tie them to one category.
NOISE = ["paytm", "razorpay", "payu", "cashfree", "upi", "pvt ltd", "private limited", "india", "ltd",
         "technologies", "services", "payment", "paid to", "online", "llp", "enterprises"]


def nonsense(rng, count):
    """Made-up words and codes ("kelvo", "zt 6ys", "breno tavik"): text the model has no reason to
    place, labelled Other, so unfamiliar names and card codes don't get a confident category from
    a few shared letters."""
    consonants, vowels = "bcdfghjklmnprstvwyz", "aeiou"

    def word():
        return "".join(rng.choice(consonants) + rng.choice(vowels) for _ in range(rng.randint(1, 4)))[: rng.randint(3, 8)]

    def code():
        return "".join(rng.choice("abcdefghijklmnopqrstuvwxyz") for _ in range(rng.randint(2, 4)))

    out = []
    for _ in range(count):
        shape = rng.random()
        if shape < 0.5:
            out.append(("Other", word()))
        elif shape < 0.8:
            out.append(("Other", f"{word()} {word()}"))
        else:
            out.append(("Other", f"{word()} *{code()}{rng.randint(1, 99)}"))
    return out


def augment(rows, seed=7):
    rng = random.Random(seed)
    out = nonsense(rng, 1200)
    for category, phrase in rows:
        out.append((category, phrase))
        out.append((category, f"{rng.choice(NOISE)} {phrase}"))
        out.append((category, f"{phrase} {rng.choice(NOISE)}"))
        out.append((category, f"{phrase.upper().replace(' ', '')}{rng.randint(10, 9999)}"))
    return out


def main():
    seed = read_phrases(HERE / "data/seed.txt")
    held = read_phrases(HERE / "data/eval.txt")
    classes = sorted({c for c, _ in seed})
    overlap = {p.lower() for _, p in seed} & {p.lower() for _, p in held}
    if overlap:
        sys.exit(f"eval.txt repeats training phrases: {sorted(overlap)[:10]}")

    train = augment(seed)
    x = vectorize([p for _, p in train])
    y = np.array([classes.index(c) for c, _ in train])
    model = LogisticRegression(C=20.0, max_iter=3000)
    model.fit(x, y)

    xe = vectorize([p for _, p in held])
    ye = np.array([classes.index(c) for c, _ in held])
    probs = model.predict_proba(xe)
    pred = probs.argmax(1)
    print(f"train phrases {len(seed)} (+augmented {len(train)}), held-out {len(held)}")
    print(f"held-out accuracy (top guess): {np.mean(pred == ye):.3f}")
    for t in (0.3, 0.4, 0.5, 0.6, 0.7):
        sure = probs.max(1) >= t
        print(f"  confidence >= {t}: answers {sure.mean():.0%}, right when it answers {np.mean(pred[sure] == ye[sure]):.3f}")

    weights = model.coef_.T.astype(np.float32)  # [DIM][classes]
    scale = float(np.abs(weights).max()) / 127.0
    quantized = np.clip(np.round(weights / scale), -127, 127).astype(np.int8)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("wb") as f:
        f.write(MAGIC)
        f.write(struct.pack("<iii", VERSION, DIM, len(classes)))
        for name in classes:
            encoded = name.encode("utf-8")
            f.write(struct.pack("<i", len(encoded)) + encoded)
        f.write(struct.pack("<f", scale))
        f.write(struct.pack(f"<{len(classes)}f", *model.intercept_.astype(np.float32)))
        f.write(quantized.tobytes())

    # What the quantized weights give, for the Kotlin parity test to match.
    deq = quantized.astype(np.float32) * scale
    scores = xe @ deq + model.intercept_.astype(np.float32)
    print(f"quantized held-out accuracy: {np.mean(scores.argmax(1) == ye):.3f}")
    print(f"wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
