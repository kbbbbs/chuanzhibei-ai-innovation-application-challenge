"""Recompute known-source retrieval metrics from a frozen dataset and sanitized live report.

This scores retrieval only. An unanswerable query returning candidates is a diagnostic,
not a failed or passed generation-abstention decision. No model API is called.
"""
from __future__ import annotations
import argparse,hashlib,json
from pathlib import Path

def source_key(source: dict) -> str:
    name=source['sourceFile']
    return (name[:-4] if name.lower().endswith('.pdf') else name)+'-p'+str(source['sourcePage'])

def channel_metrics(items: list[tuple[dict,dict]], channel: str, keys: dict[str,str]) -> dict:
    ranks=[]
    for question,row in items:
        expected={keys[name] for name in question['goldSourceIds']}
        ranks.append(next((index for index,key in enumerate(row.get(channel+'Sources',[])[:5],1) if key in expected),None))
    n=len(ranks)
    return {'knownSourceTop5Hits':sum(rank is not None for rank in ranks),
            'knownSourceTop5Rate':sum(rank is not None for rank in ranks)/n if n else None,
            'knownSourceTop1Hits':sum(rank==1 for rank in ranks),
            'mrrAt5':sum(1/rank for rank in ranks if rank is not None)/n if n else None}

def evaluate(dataset_file: Path,report_file: Path) -> dict:
    content=Path(dataset_file).read_bytes();dataset=json.loads(content)
    report=json.loads(Path(report_file).read_text(encoding='utf-8'))
    digest=hashlib.sha256(content).hexdigest()
    if report.get('datasetSha256')!=digest:raise ValueError('Dataset differs from the frozen live-run input')
    questions=dataset['questions'];rows=report['questions']
    known={q['id'] for q in questions};by_id={row['id']:row for row in rows}
    if len(by_id)!=len(rows) or len(known)!=len(questions):raise ValueError('Duplicate question IDs')
    if known!=set(by_id):raise ValueError('Incomplete or unknown query results; partial runs are not scored as complete acceptance')
    keys={source['id']:source_key(source) for source in dataset['sources']}
    result={'datasetSha256':digest,'abstentionEvaluated':False,
            'interpretation':'Known annotated source-page presence in returned top5; not full relevance annotation or answer accuracy.'}
    for split in ['tuning','acceptance']:
        selected=[(q,by_id[q['id']]) for q in questions if q['split']==split]
        supported=[pair for pair in selected if pair[0]['answerable']]
        absent=[pair for pair in selected if not pair[0]['answerable']]
        result[split]={'answerable':len(supported),'unanswerable':len(absent),
                       'hybrid':channel_metrics(supported,'hybrid',keys),'bm25':channel_metrics(supported,'bm25',keys),
                       'noAnswerQueriesWithHybridCandidates':sum(bool(row.get('hybridSources')) for _,row in absent),
                       'noAnswerQueriesWithBm25Candidates':sum(bool(row.get('bm25Sources')) for _,row in absent)}
    return result

def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dataset',type=Path,required=True);parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args();text=json.dumps(evaluate(args.dataset,args.report),ensure_ascii=False,indent=2)+'\n'
    if args.output:args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(text,encoding='utf-8')
    else:print(text,end='')

if __name__=='__main__':main()
