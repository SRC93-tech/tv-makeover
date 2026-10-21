#!/usr/bin/env python3
"""Per-frame CPU and GPU times from `dumpsys gfxinfo <pkg> framestats` dumps (the last 120 frames of each).

Usage: python3 -I frames.py dump1.txt [dump2.txt ...]
cpu = IntendedVsync -> FrameCompleted; gpu = IssueDrawCommandsStart -> GpuCompleted (includes waiting for the
previous frame's GPU work, so it shows when the GPU is the bottleneck). Prefer this over the gfxinfo GPU
histogram, whose last bucket (4950ms) collects unmeasured frames and makes p90/p99 meaningless.
"""
import sys

cpu, gpu = [], []
for path in sys.argv[1:]:
    lines = open(path).read().splitlines()
    for i, line in enumerate(lines):
        if not line.startswith('Flags,'):
            continue
        cols = line.rstrip(',').split(',')
        for row in lines[i + 1:]:
            if row.startswith('---'):
                break
            v = row.rstrip(',').split(',')
            if len(v) < len(cols) or v[0] != '0':
                continue
            d = dict(zip(cols, map(int, v[:len(cols)])))
            cpu.append((d['FrameCompleted'] - d['IntendedVsync']) / 1e6)
            if d.get('GpuCompleted', 0) > 0:
                gpu.append((d['GpuCompleted'] - d['IssueDrawCommandsStart']) / 1e6)


def pct(a, p):
    a = sorted(a)
    return round(a[min(len(a) - 1, int(len(a) * p))], 1) if a else None


print(f'frames {len(cpu)}  cpu p50/p90/p99 {pct(cpu, .5)}/{pct(cpu, .9)}/{pct(cpu, .99)} ms  '
      f'gpu p50/p90/p99 {pct(gpu, .5)}/{pct(gpu, .9)}/{pct(gpu, .99)} ms  '
      f'gpu over 16.7ms: {sum(g > 16.7 for g in gpu)}/{len(gpu)}')
