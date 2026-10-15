import _isNil from 'lodash-es/isNil'

const hasDocument = typeof document === 'object' && document !== null
const hasWindow = typeof window === 'object' && window !== null && window.self === window

const isBrowser = (() => {
  const fn = (() => {
    return !_isNil(fn.override) ? (fn.override as boolean) : hasDocument && hasWindow
  }) as { (): boolean; override?: boolean }

  return fn
})()

export default isBrowser
