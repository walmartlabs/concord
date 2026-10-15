import _invoke from 'lodash-es/invoke'
import React from 'react'
import Table from '@/components/ui/collections/Table/Table'
import { OnCellHoverData, OnValueClickData } from '../BaseCalendarView'

const hoverCellStyles = {
  outline: '1px solid #85b7d9',
  cursor: 'pointer',
}

export interface CellWidthStyle {
  width: string
}

export const cellStyleWidth3: CellWidthStyle = {
  width: '33.333333%',
}

export const cellStyleWidth4: CellWidthStyle = {
  width: '25%',
}

export const cellStyleWidth7: CellWidthStyle = {
  width: '14.285714%',
}

interface CellProps {
  /** Position of a cell on the page. (Used by parent component) */
  itemPosition: number

  /** Cell's content. */
  content: string

  /** Styles for cell width. */
  style?: CellWidthStyle

  /** Called after click on a cell. */
  onClick: (e: React.SyntheticEvent<HTMLElement>, data: OnValueClickData) => void

  /** Called on cell hover. */
  onHover: (e: React.SyntheticEvent<HTMLElement>, data: OnCellHoverData) => void

  /** Is cell is hovered. */
  hovered?: boolean

  /** Is cell active. */
  active?: boolean

  /** Is cell disabled. */
  disabled?: boolean
}

/** Legacy calendar props accepted from untyped callers; they never reach the cell. */
type DroppedCellProp = 'markColor' | 'marked'

function Cell(props: CellProps): React.ReactElement {
  const {
    itemPosition,
    content,
    style,
    onClick,
    onHover,
    hovered,
    ...rest
  } = props as CellProps & Record<DroppedCellProp, unknown>

  const onCellClick = (event: React.SyntheticEvent<HTMLElement>): void => {
    _invoke(props, 'onClick', event, { ...props, itemPosition, value: content })
  }

  const onCellHover = (event: React.SyntheticEvent<HTMLElement>): void => {
    _invoke(props, 'onHover', event, { ...props, itemPosition })
  }

  const cellStyle = { ...style, ...(hovered ? hoverCellStyles : {}) }

  return React.createElement(
    Table.Cell,
    { ...rest, style: cellStyle, onMouseOver: onCellHover, onClick: onCellClick },
    React.createElement('span', { className: 'suicr-content-item' }, content),
  )
}

export default Cell
