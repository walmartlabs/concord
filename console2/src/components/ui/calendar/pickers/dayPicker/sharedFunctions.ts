import _find from 'lodash-es/find'
import _first from 'lodash-es/first'
import _indexOf from 'lodash-es/indexOf'
import _lastIndexOf from 'lodash-es/lastIndexOf'
import _range from 'lodash-es/range'
import _slice from 'lodash-es/slice'
import { Moment } from 'moment'

/** Build days to fill page. */
export function buildDays(date: Moment, daysOnPage: number): string[] {
  const start = date.clone().startOf('month').startOf('week')

  return getDaysArray(start.date(), getBrakepoints(date), daysOnPage).map((d) => d.toString())
}

/** Return dates from ends of months. */
function getBrakepoints(referenceDate: Moment): number[] {
  const dateInCurrentMonth = referenceDate.clone()
  const currentMonth = dateInCurrentMonth.month()
  const brakepoints: number[] = []
  const firstDateOnPage = dateInCurrentMonth.clone().startOf('month').startOf('week')

  if (firstDateOnPage.month() !== currentMonth) {
    brakepoints.push(firstDateOnPage.clone().endOf('month').date())
  }
  brakepoints.push(dateInCurrentMonth.clone().endOf('month').date())

  return brakepoints
}

/* Return array of day positions that belong to the displayed month. */
export function getDefaultEnabledDayPositions(allDays: string[], date: Moment): number[] {
  const brakepoints = getBrakepoints(date.clone())

  if (brakepoints.length === 1) {
    return _range(0, _indexOf(allDays, brakepoints[0].toString()) + 1)
  }

  return _range(
    _indexOf(allDays, brakepoints[0].toString()) + 1,
    _lastIndexOf(allDays, brakepoints[1].toString()) + 1,
  )
}

function getDaysArray(start: number, brakepoints: number[], length: number): number[] {
  let currentDay = start
  const days: number[] = []
  let brakepointsLeft = brakepoints.slice()

  while (days.length !== length) {
    days.push(currentDay)

    const breakpoint = _first(brakepointsLeft)

    if (currentDay === breakpoint) {
      currentDay = 1
      brakepointsLeft = _slice(brakepointsLeft, 1)
    } else {
      currentDay += 1
    }
  }

  return days
}

export function getInitialDatePosition(
  initDate: string,
  values: string[],
  selectablePositions: number[],
): number {
  const selectable = selectablePositions.reduce<{ value: string; position: number }[]>(
    (acc, pos) => {
      acc.push({ value: values[pos], position: pos })

      return acc
    },
    [],
  )
  const result = _find(selectable, (item) => item.value === initDate)

  return result ? result.position : selectable[0].position
}
