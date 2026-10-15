import _isArray from 'lodash-es/isArray'
import _isNil from 'lodash-es/isNil'
import React from 'react'
import Table from '@/components/ui/collections/Table/Table'
import { OnCellHoverData, OnValueClickData } from '../BaseCalendarView'
import Cell, { CellWidthStyle, cellStyleWidth3, cellStyleWidth4, cellStyleWidth7 } from './Cell'

export type BodyWidth = 3 | 4 | 7

interface BodyProps {
  /** A number of columns in a row. */
  width: BodyWidth

  /** Data that is used to fill a calendar. */
  data: string[]

  /** Called after a click on calendar's cell. */
  onCellClick: (e: React.SyntheticEvent<HTMLElement>, data: OnValueClickData) => void

  /** Called on cell hover. */
  onCellHover: (e: React.SyntheticEvent<HTMLElement>, data: OnCellHoverData) => void

  /** Index of an element in `data` array that should be displayed as hovered. */
  hovered?: number

  /** Index of an element (or array of indexes) in `data` array that should be displayed as active. */
  active?: number | number[]

  /** Array of element indexes in `data` array that should be displayed as disabled. */
  disabled?: number[]
}

function Body(props: BodyProps): React.ReactElement {
  const { data, width, onCellClick, active, disabled, hovered, onCellHover } = props

  const content = buildRows(data, width).map((row, rowIndex) =>
    React.createElement(
      Table.Row,
      { key: `${rowIndex}${row[0]}` },
      row.map((item, itemIndex) =>
        React.createElement(Cell, {
          style: getCellStyle(width),
          active: isActive(rowIndex, width, itemIndex, active),
          hovered: isHovered(rowIndex, width, itemIndex, hovered),
          disabled: isDisabled(rowIndex, width, itemIndex, disabled),
          key: `${rowIndex * width + itemIndex}`,
          itemPosition: rowIndex * width + itemIndex,
          content: item,
          onHover: onCellHover,
          onClick: onCellClick,
        }),
      ),
    ),
  )

  return React.createElement(Table.Body, null, content)
}

function buildRows(data: string[], width: BodyWidth): string[][] {
  const height = data.length / width
  const rows: string[][] = []

  for (let i = 0; i < height; i++) {
    rows.push(data.slice(i * width, i * width + width))
  }

  return rows
}

function isActive(
  rowIndex: number,
  rowWidth: BodyWidth,
  colIndex: number,
  active?: number | number[],
): boolean {
  if (_isNil(active)) {
    return false
  }

  if (_isArray(active)) {
    for (const activeIndex of active) {
      if (rowIndex * rowWidth + colIndex === activeIndex) {
        return true
      }
    }
  }

  return rowIndex * rowWidth + colIndex === active
}

function isHovered(
  rowIndex: number,
  rowWidth: BodyWidth,
  colIndex: number,
  hovered?: number,
): boolean {
  if (_isNil(hovered)) {
    return false
  }

  return rowIndex * rowWidth + colIndex === hovered
}

function isDisabled(
  rowIndex: number,
  rowWidth: BodyWidth,
  colIndex: number,
  disabledIndexes?: number[],
): boolean {
  if (_isNil(disabledIndexes) || disabledIndexes.length === 0) {
    return false
  }

  for (const disabledIndex of disabledIndexes) {
    if (rowIndex * rowWidth + colIndex === disabledIndex) {
      return true
    }
  }

  return false
}

function getCellStyle(width: BodyWidth): CellWidthStyle | undefined {
  switch (width) {
    case 3:
      return cellStyleWidth3
    case 4:
      return cellStyleWidth4
    case 7:
      return cellStyleWidth7
    default:
      break
  }
}

export default Body
