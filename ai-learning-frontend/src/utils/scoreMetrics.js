// Scores are only comparable as a share of their own full score. Matches the backend pass ratio.
const PASS_PERCENT = 60

const PERCENT_BUCKETS = [
  { range: '0-59', below: 60 },
  { range: '60-69', below: 70 },
  { range: '70-79', below: 80 },
  { range: '80-89', below: 90 },
  { range: '90-100', below: Infinity },
]

// Submissions snapshot every paper question at start, so answers[].maxScore is the real full
// score for that attempt. Anything incomplete stays unknown instead of being assumed to be 100.
export function submissionFullScore(submission) {
  const answers = submission?.answers
  if (!Array.isArray(answers) || !answers.length) return null
  let total = 0
  for (const answer of answers) {
    const maxScore = answer?.maxScore === null || answer?.maxScore === undefined || answer?.maxScore === ''
      ? NaN
      : Number(answer.maxScore)
    if (!Number.isFinite(maxScore) || maxScore < 0) return null
    total += maxScore
  }
  return total > 0 ? Math.round(total * 100) / 100 : null
}

// For a full score declared by a list response; the backend does not always send one.
export function declaredFullScore(value) {
  const fullScore = Number(value)
  return Number.isFinite(fullScore) && fullScore > 0 ? fullScore : null
}

export function scorePercent(score, fullScore) {
  if (score === null || score === undefined || score === '' || !(fullScore > 0)) return null
  const value = Number(score)
  if (!Number.isFinite(value) || value < 0) return null
  return value * 100 / fullScore
}

export function roundPercent(value) {
  return value === null || value === undefined ? null : Math.round(value * 10) / 10
}

export function formatPercent(value) {
  return value === null || value === undefined ? '—' : `${roundPercent(value)}%`
}

export function meanPercent(percents) {
  return percents.length ? percents.reduce((sum, value) => sum + value, 0) / percents.length : null
}

export function isPassing(percent) {
  return percent >= PASS_PERCENT
}

export function passRate(percents) {
  return percents.length ? percents.filter(isPassing).length * 100 / percents.length : null
}

export function percentDistribution(percents) {
  if (!percents.length) return []
  const counts = PERCENT_BUCKETS.map(({ range }) => ({ range, count: 0 }))
  percents.forEach((percent) => {
    counts[PERCENT_BUCKETS.findIndex(bucket => percent < bucket.below)].count += 1
  })
  return counts
}
