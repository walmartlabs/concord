
import React from 'react'
import { getUnhandledProps, isBrowser } from '../../lib'
import Portal from '../../addons/Portal'
import DimmerInner from './DimmerInner'

export interface DimmerProps extends StrictDimmerProps, React.HTMLAttributes<HTMLDivElement> {}

export interface StrictDimmerProps {
  /** An active dimmer will dim its parent container. */
  active?: boolean

  /** A dimmer can be formatted to have its colors inverted. */
  inverted?: boolean

  /** A dimmer can be formatted to be fixed to the page. */
  page?: boolean
}

/**
 * A dimmer hides distractions to focus attention on particular content.
 */
function Dimmer(props: DimmerProps): React.ReactElement {
  const handlePortalMount = (): void => {
    if (!isBrowser()) return

    // Heads up, IE doesn't support second argument in add()
    document.body.classList.add('dimmed')
    document.body.classList.add('dimmable')
  }

  const handlePortalUnmount = (): void => {
    if (!isBrowser()) return

    // Heads up, IE doesn't support second argument in add()
    document.body.classList.remove('dimmed')
    document.body.classList.remove('dimmable')
  }

  const { active, inverted, page } = props
  const rest = getUnhandledProps(Dimmer, props)

  if (page) {
    return (
      <Portal
        closeOnEscape={false}
        closeOnDocumentClick={false}
        onMount={handlePortalMount}
        onUnmount={handlePortalUnmount}
        open={active}
        openOnTriggerClick={false}
      >
        <DimmerInner {...rest} active={active} inverted={inverted} page={page} />
      </Portal>
    )
  }

  return <DimmerInner {...rest} active={active} inverted={inverted} page={page} />
}

Dimmer.handledProps = ['active', 'inverted', 'page']



export default Dimmer
