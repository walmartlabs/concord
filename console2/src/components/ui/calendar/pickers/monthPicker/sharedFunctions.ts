import moment from 'moment'

const buildCalendarValues = (localization?: string): string[] => {
  const localLocale = localization ? moment.localeData(localization) : undefined

  return localLocale ? localLocale.monthsShort() : moment.monthsShort()
}

const getInitialDatePosition = (selectable: number[], currentDate: moment.Moment): number => {
  if (selectable.indexOf(currentDate.month()) < 0) {
    return selectable[0]
  }

  return currentDate.month()
}

export { buildCalendarValues, getInitialDatePosition }
