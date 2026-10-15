
import React from 'react'
import { SemanticCOLORS, SemanticICONS } from '../../generic'
import { getUnhandledProps, SUI } from '../../lib'
import Icon from '../Icon/Icon'

export interface ListIconProps {
  className?: string
  color?: SemanticCOLORS
  name?: SemanticICONS
}

/**
 * A list item can contain an icon.
 */
function ListIcon(props: ListIconProps): React.ReactElement {
  const { className } = props

  const rest = getUnhandledProps(ListIcon, props)

  return React.createElement(Icon, { ...rest, className })
}

ListIcon.handledProps = ['as', 'className']



export default ListIcon
