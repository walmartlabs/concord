
import _some from 'lodash-es/some'
import { Children } from 'react'

/**
 * Determine if child by type exists in children.
 */
export const someByType = (
  children: React.ReactNode,
  type: string | React.ComponentType,
): boolean => _some(Children.toArray(children), { type })

/**
 * Tests if children are nil in React and Preact.
 */
export const isNil = (children: React.ReactNode): boolean =>
  children === null ||
  children === undefined ||
  (Array.isArray(children) && children.length === 0)
