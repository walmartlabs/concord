import _isNil from 'lodash-es/isNil'
import _isString from 'lodash-es/isString'
import moment, { Moment } from 'moment'

/** Parse string, moment, Date.
 *
 * Return unedfined on invalid input.
 */
export function parseValue(
  value: string | Moment | Date,
  dateFormat: string,
  localization: string,
): Moment | undefined {
  if (!_isNil(value) && !_isNil(dateFormat)) {
    const date = moment(value, dateFormat)

    if (date.isValid()) {
      date.locale(localization)

      return date
    }
  }
}

/** Creates a Moment from the selected value or the default value. */
export function buildValue(
  value: string | Moment | Date,
  localization: string,
  dateFormat: string,
  defaultVal: Moment | null = moment(),
): Moment | undefined {
  const valueParsed = parseValue(value, dateFormat, localization)

  if (valueParsed) {
    return valueParsed
  }

  const result = defaultVal ? defaultVal.clone() : defaultVal

  if (result) {
    result.locale(localization)
  }

  return result || undefined
}

export function dateValueToString(
  value: string | Moment | Date,
  dateFormat: string,
  locale: string,
): string {
  if (_isString(value)) {
    return value
  }

  if (moment.isMoment(value)) {
    const momentValue = value.clone()

    momentValue.locale(locale)

    return momentValue.format(dateFormat)
  }

  const date = moment(value, dateFormat)

  if (date.isValid()) {
    date.locale(locale)

    return date.format(dateFormat)
  }

  return ''
}
