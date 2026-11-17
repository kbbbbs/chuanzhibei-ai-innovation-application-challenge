"""Summarize a frozen 20-question course retrieval run without a score threshold."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def summarize(dataset: dict, report: dict) -> dict:
    questions = dataset['questions']
    rows = report['questions']
    expected_ids = [question['id'] for question in questions]
    actual_ids = [row['id'] for row in rows]
    if len(expected_ids) != 20 or len(set(expected_ids)) != 20:
        raise ValueError('The frozen course measurement must contain 20 unique questions')
    if len(set(actual_ids)) != len(actual_ids) or set(actual_ids) != set(expected_ids):
        raise ValueError('All frozen questions require exactly one result')
    by_id = {row['id']: row for row in rows}
    result = {'questionCount': len(questions), 'metric': 'Annotated source-page presence in top5',
              'qualityThreshold': None, 'answerAccuracyEvaluated': False}
    for channel, required_mode in [('hybrid', 'HYBRID'), ('keyword', 'KEYWORD')]:
        ranks = []
        modes = {}
        failed = 0
        durations = []
        for question in questions:
            observation = by_id[question['id']][channel]
            mode = observation.get('actualMode', 'ERROR')
            modes[mode] = modes.get(mode, 0) + 1
            if observation.get('errorCode') or mode == 'ERROR':
                failed += 1
            pages = observation.get('pages', [])[:5] if mode == required_mode else []
            rank = next((index for index, page in enumerate(pages, 1)
                         if page in question['goldPages']), None)
            ranks.append(rank)
            if observation.get('elapsedMs') is not None:
                durations.append(observation['elapsedMs'])
        count = len(questions)
        hits = sum(rank is not None for rank in ranks)
        top1 = sum(rank == 1 for rank in ranks)
        result[channel] = {
            'attempted': count, 'completedInRequestedMode': modes.get(required_mode, 0),
            'actualModes': modes, 'failed': failed,
            'degraded': sum(amount for mode, amount in modes.items()
                            if mode not in (required_mode, 'ERROR')),
            'top5Hits': hits, 'top5Rate': hits / count,
            'top1Hits': top1, 'top1Rate': top1 / count,
            'mrrAt5': sum(1 / rank for rank in ranks if rank is not None) / count,
            'meanElapsedMs': sum(durations) / len(durations) if durations else None,
        }
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dataset', required=True, type=Path)
    parser.add_argument('--report', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    dataset_bytes = args.dataset.read_bytes()
    dataset = json.loads(dataset_bytes)
    report = json.loads(args.report.read_text(encoding='utf-8'))
    digest = hashlib.sha256(dataset_bytes).hexdigest()
    if report.get('datasetSha256') != digest:
        raise ValueError('Run input does not match the frozen dataset')
    result = summarize(dataset, report)
    result['datasetSha256'] = digest
    result['runStatus'] = report.get('status')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
