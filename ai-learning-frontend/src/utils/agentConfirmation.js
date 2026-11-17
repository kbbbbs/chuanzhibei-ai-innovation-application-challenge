const hasValue = value => value !== null && value !== undefined && value !== ''

// Choose the fields once when opening the dialog so clearing an input keeps it editable.
export function confirmationFields(schema = [], parameters = {}, rawRequest = '', requiredAnyOf = []) {
  const request = String(rawRequest).toLowerCase()
  const selected = new Set()
  for (const field of schema) {
    const value = parameters[field.name]
    const mentioned = !request || request.includes(String(value).toLowerCase())
      || [field.label, ...(field.aliases || [])].some(label => label && request.includes(String(label).toLowerCase()))
    if ((hasValue(value) && mentioned) || (field.required && !hasValue(value))) selected.add(field.name)
  }
  for (const group of requiredAnyOf) {
    if (group.some(name => hasValue(parameters[name]))) continue
    const candidates = schema.filter(field => group.includes(field.name))
    const field = candidates.find(field => !/id$/i.test(field.name)) || candidates[0]
    if (field) selected.add(field.name)
  }
  return schema.filter(field => selected.has(field.name))
}
