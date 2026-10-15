import { isRefObject } from '@fluentui/react-component-ref'
import React from 'react'
import useIsomorphicLayoutEffect from './useIsomorphicLayoutEffect'

const CLASS_NAME_DELIMITER = /\s+/

type ClassNameRef = React.RefObject<string | undefined>

/**
 * Accepts a set of ref objects that contain classnames as a string and returns an array of unique
 * classNames.
 */
export function computeClassNames(
  classNameRefs: Set<ClassNameRef> | undefined,
): string[] {
  const classNames: string[] = []

  if (classNameRefs) {
    classNameRefs.forEach((classNameRef) => {
      if (typeof classNameRef.current === 'string') {
        const classNamesForRef = classNameRef.current.split(CLASS_NAME_DELIMITER)

        classNamesForRef.forEach((className) => {
          classNames.push(className)
        })
      }
    })

    return classNames.filter(
      (className, i, array) => className.length > 0 && array.indexOf(className) === i,
    )
  }

  return []
}

/**
 * Computes classnames that should be removed and added to a node based on input differences.
 */
export function computeClassNamesDifference(
  prevClassNames: string[],
  currentClassNames: string[],
): [string[], string[]] {
  return [
    currentClassNames.filter((className) => prevClassNames.indexOf(className) === -1),
    prevClassNames.filter((className) => currentClassNames.indexOf(className) === -1),
  ]
}

const prevClassNames = new Map<HTMLElement | null | undefined, string[]>()

export const handleClassNamesChange = (
  node: HTMLElement | null | undefined,
  classNameRefs: Set<ClassNameRef> | undefined,
): void => {
  const currentClassNames = computeClassNames(classNameRefs)

  const [forAdd, forRemoval] = computeClassNamesDifference(
    prevClassNames.get(node) || [],
    currentClassNames,
  )

  if (node) {
    forAdd.forEach((className) => node.classList.add(className))
    forRemoval.forEach((className) => node.classList.remove(className))
  }

  prevClassNames.set(node, currentClassNames)
}

export class NodeRegistry {
  nodes = new Map<HTMLElement | null | undefined, Set<ClassNameRef>>()

  add = (node: HTMLElement | null | undefined, classNameRef: ClassNameRef): void => {
    if (this.nodes.has(node)) {
      const set = this.nodes.get(node)
      set?.add(classNameRef)

      return
    }

    // IE11 does not support constructor params
    const set = new Set<ClassNameRef>()
    set.add(classNameRef)

    this.nodes.set(node, set)
  }

  del = (node: HTMLElement | null | undefined, classNameRef: ClassNameRef): void => {
    if (!this.nodes.has(node)) return

    const set = this.nodes.get(node)

    if (set?.size === 1) {
      this.nodes.delete(node)

      return
    }

    set?.delete(classNameRef)
  }

  emit = (
    node: HTMLElement | null | undefined,
    callback: (
      node: HTMLElement | null | undefined,
      classNameRefs: Set<ClassNameRef> | undefined,
    ) => void,
  ): void => {
    callback(node, this.nodes.get(node))
  }
}

const nodeRegistry = new NodeRegistry()

/**
 * A React hooks that allows to manage classNames on a DOM node in declarative manner. Accepts
 * a HTML element or React ref objects with it.
 */
export default function useClassNamesOnNode(
  node: HTMLElement | React.RefObject<HTMLElement | null> | null,
  className: string,
): void {
  const classNameRef = React.useRef<string | undefined>(undefined)
  const isMounted = React.useRef(false)

  useIsomorphicLayoutEffect(() => {
    classNameRef.current = className

    if (isMounted.current) {
      const element: HTMLElement | null | undefined = isRefObject(node)
        ? node.current
        : node
      nodeRegistry.emit(element, handleClassNamesChange)
    }

    isMounted.current = true
  }, [className])

  useIsomorphicLayoutEffect(() => {
    const element: HTMLElement | null | undefined = isRefObject(node)
      ? node.current
      : node

    nodeRegistry.add(element, classNameRef)
    nodeRegistry.emit(element, handleClassNamesChange)

    return () => {
      nodeRegistry.del(element, classNameRef)
      nodeRegistry.emit(element, handleClassNamesChange)
    }
  }, [node])
}
