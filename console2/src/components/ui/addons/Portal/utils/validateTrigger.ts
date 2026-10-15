import React from 'react'

/**
 * Asserts that a passed element can be used cloned a props will be applied properly.
 */
export default function validateTrigger(element: React.ReactNode): void {
  if (element) {
    const trigger = React.Children.only(element)

    if (React.isValidElement(trigger) && trigger.type === React.Fragment) {
      throw new Error('An "React.Fragment" cannot be used as a `trigger`.')
    }
  }
}
