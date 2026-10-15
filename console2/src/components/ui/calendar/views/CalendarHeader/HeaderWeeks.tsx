import moment from 'moment'
import React from 'react'
import Table from '@/components/ui/collections/Table/Table'

/** Return array of week day names.
 *
 * getWeekDays() --> ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Su']
 */
function getWeekDays(m: typeof moment, localization?: string): string[] {
  const weekDays: string[] = []
  const day = localization ? m().locale(localization).startOf('week') : m().startOf('week')

  for (let i = 0; i < 7; i++) {
    weekDays[i] = day.format('dd')
    day.add(1, 'd')
  }

  return weekDays
}

const cellStyle = {
  border: 'none',
  borderBottom: '1px solid rgba(34,36,38,.1)',
}

export interface HeaderWeeksProps {
  /** Moment date localization */
  localization?: string
}

function HeaderWeeks(props: HeaderWeeksProps): React.ReactElement {
  const { localization } = props

  return React.createElement(
    Table.Row,
    null,
    getWeekDays(moment, localization).map((weekDay) =>
      React.createElement(Table.HeaderCell, { key: weekDay, style: cellStyle, colSpan: 1 }, weekDay),
    ),
  )
}

export default HeaderWeeks
