import { onBeforeUnmount, ref } from 'vue'

const NARROW_QUERY = '(max-width: 720px)'

// Width of an action column once RowActions has collapsed it to a single button.
export const ROW_SHEET_COLUMN_WIDTH = 84

// Reactive so layouts driven by props, not CSS, switch on resize without a reload.
export function useNarrowScreen() {
  const query = window.matchMedia(NARROW_QUERY)
  const narrow = ref(query.matches)
  const sync = (event) => { narrow.value = event.matches }
  query.addEventListener('change', sync)
  onBeforeUnmount(() => query.removeEventListener('change', sync))
  return narrow
}
