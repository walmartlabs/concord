import { ReactInstance } from 'react'
import * as ReactDOM from 'react-dom'

export default function findHTMLElement(e: ReactInstance | null): HTMLElement | undefined {
  const el = ReactDOM.findDOMNode(e) as HTMLElement | null

  if (el && typeof el.focus === 'function') {
    return el
  }

  return undefined
}
