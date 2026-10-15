import _first from 'lodash-es/first'
import _last from 'lodash-es/last'
import React from 'react'
import {
  BaseCalendarViewProps,
  CalendarWithHeaderViewProps,
  SingleSelectionCalendarViewProps,
  useCalendarView,
} from './BaseCalendarView'
import Calendar from './Calendar'
import Body from './CalendarBody/Body'
import Header from './CalendarHeader/Header'

const YEAR_CALENDAR_ROW_WIDTH = 3

type YearViewProps = BaseCalendarViewProps &
  SingleSelectionCalendarViewProps &
  CalendarWithHeaderViewProps

/** Legacy calendar prop accepted from untyped callers; it never reaches the table. */
type DroppedViewProp = 'inline'

function YearView(props: YearViewProps): React.ReactElement {
  const {
    values,
    onNextPageBtnClick,
    onPrevPageBtnClick,
    onValueClick,
    hasNextPage,
    hasPrevPage,
    onHeaderClick,
    disabledItemIndexes,
    activeItemIndex,
    hoveredItemIndex,
    onCellHover,
    onMount,
    inline,
    localization,
    ...rest
  } = props as YearViewProps & Record<DroppedViewProp, unknown>

  const setCalendarNode = useCalendarView(onMount)

  const headerTitle = `${_first(values)} - ${_last(values)}`

  return React.createElement(
    Calendar,
    { ref: setCalendarNode, ...rest },
    React.createElement(Header, {
      className: 'suicr-year-view-header',
      title: headerTitle,
      onNextPageBtnClick,
      onPrevPageBtnClick,
      hasNextPage,
      hasPrevPage,
      onHeaderClick,
      width: YEAR_CALENDAR_ROW_WIDTH,
      displayWeeks: false,
      localization,
    }),
    React.createElement(Body, {
      width: YEAR_CALENDAR_ROW_WIDTH,
      data: values,
      hovered: hoveredItemIndex,
      onCellHover,
      onCellClick: onValueClick,
      active: activeItemIndex,
      disabled: disabledItemIndexes,
    }),
  )
}

export default YearView
