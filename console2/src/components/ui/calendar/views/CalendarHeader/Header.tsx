import React from 'react'
import Table from '@/components/ui/collections/Table/Table'
import Icon from '@/components/ui/elements/Icon/Icon'
import { BodyWidth } from '../CalendarBody/Body'
import HeaderWeeks from './HeaderWeeks'

export interface HeaderProps {
  /** Header text content. */
  title: string

  /** Called after click on next page button. */
  onNextPageBtnClick: () => void

  /** Called after click on previous page button. */
  onPrevPageBtnClick: () => void

  /** Whether to display previous page button as active or disabled. */
  hasPrevPage: boolean

  /** Whether to display next page button as active or disabled. */
  hasNextPage: boolean

  /** Whether to display weeks row or not. */
  displayWeeks: boolean

  /** Header width. */
  width: BodyWidth

  /** Called after click on calendar header. */
  onHeaderClick?: () => void

  /** Moment date localization */
  localization?: string

  className?: string
}

function Header(props: HeaderProps): React.ReactElement {
  const {
    displayWeeks,
    onNextPageBtnClick,
    onPrevPageBtnClick,
    hasPrevPage,
    hasNextPage,
    onHeaderClick,
    width,
    title,
    localization,
    className,
  } = props

  const cellStyle = {
    border: 'none',
    borderBottom: displayWeeks ? 'none' : '1px solid rgba(34,36,38,.1)',
  }
  const prevPageBtnStyle = {
    cursor: hasPrevPage ? 'pointer' : 'auto',
  }
  const nextPageBtnStyle = {
    cursor: hasNextPage ? 'pointer' : 'auto',
  }
  const headerTitleStyle = {
    cursor: onHeaderClick ? 'pointer' : 'default',
  }

  return React.createElement(
    Table.Header,
    { className },
    React.createElement(
      Table.Row,
      null,
      React.createElement(
        Table.HeaderCell,
        { style: cellStyle, colSpan: 1 },
        React.createElement(Icon, {
          fitted: true,
          style: prevPageBtnStyle,
          disabled: !hasPrevPage,
          onClick: hasPrevPage ? onPrevPageBtnClick : undefined,
          name: 'chevron left',
        }),
      ),
      React.createElement(
        Table.HeaderCell,
        { onClick: onHeaderClick ? onHeaderClick : undefined, style: cellStyle, colSpan: width - 2 },
        React.createElement('span', { style: headerTitleStyle }, title),
      ),
      React.createElement(
        Table.HeaderCell,
        { style: cellStyle, colSpan: 1 },
        React.createElement(Icon, {
          fitted: true,
          style: nextPageBtnStyle,
          disabled: !hasNextPage,
          onClick: hasNextPage ? onNextPageBtnClick : undefined,
          name: 'chevron right',
        }),
      ),
    ),
    displayWeeks && React.createElement(HeaderWeeks, { localization }),
  )
}

export default Header
