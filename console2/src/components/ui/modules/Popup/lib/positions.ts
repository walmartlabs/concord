import _invert from 'lodash-es/invert'
import _keys from 'lodash-es/keys'

export const positionsMapping = {
  'top center': 'top',
  'top left': 'top-start',
  'top right': 'top-end',
  'bottom center': 'bottom',
  'bottom left': 'bottom-start',
  'bottom right': 'bottom-end',
  'right center': 'right',
  'left center': 'left',
} as const

export const positions = _keys(positionsMapping) as (keyof typeof positionsMapping)[]

export const placementMapping = _invert(positionsMapping)
