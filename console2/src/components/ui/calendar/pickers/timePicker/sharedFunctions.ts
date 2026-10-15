import { Moment } from 'moment'

export function buildTimeStringWithSuffix(hour: string, minute: string): string {
  return `${hour}:${minute}`
}

export function getCurrentDate(date: Moment): string {
  return date.format('MMMM DD, YYYY')
}
