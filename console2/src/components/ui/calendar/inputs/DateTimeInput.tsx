
import React from 'react'
import DayPicker from '../pickers/dayPicker/DayPicker'
import MonthPicker from '../pickers/monthPicker/MonthPicker'
import HourPicker from '../pickers/timePicker/HourPicker'
import MinutePicker from '../pickers/timePicker/MinutePicker'
import YearPicker from '../pickers/YearPicker'
import InputView from '../views/InputView'
import {
  BaseInputDefaultProps,
  BaseInputProps,
  DateInputConfig,
  useDateInput,
} from './BaseInput'
import { dateValueToString } from './parse'

type CalendarMode = 'year' | 'month' | 'day' | 'hour' | 'minute'

export interface DateTimeInputProps extends BaseInputProps {
  dateFormat?: string

  /** Datetime formatting string. */
  dateTimeFormat?: string
}

export type DateTimeInputOnChangeData = DateTimeInputProps

const nextMode: Record<CalendarMode, CalendarMode> = {
  year: 'month',
  month: 'day',
  day: 'hour',
  hour: 'minute',
  minute: 'year',
}

const prevMode: Record<CalendarMode, CalendarMode> = {
  minute: 'hour',
  hour: 'day',
  day: 'month',
  month: 'year',
  year: 'minute',
}

/** Legacy calendar props accepted from untyped callers; they never reach the input. */
type DroppedDateTimeInputProp =
  | 'disable'
  | 'disableMinute'
  | 'divider'
  | 'initialDate'
  | 'inline'
  | 'markColor'
  | 'marked'
  | 'maxDate'
  | 'minDate'
  | 'preserveViewMode'
  | 'startMode'
  | 'timeFormat'

const DATE_TIME_INPUT_CONFIG: DateInputConfig<CalendarMode> = {
  getFormat: (props) => props.dateTimeFormat || `${props.dateFormat} HH:mm`,

  getDateValues: (value) => ({
    year: value.year(),
    month: value.month(),
    date: value.date(),
    hour: value.hour(),
    minute: value.minute(),
  }),

  getNextMode: (mode) => nextMode[mode],
  getPrevMode: (mode) => prevMode[mode],
  commitMode: 'minute',
  // The `Popup` computes its content size on click, the delayed update avoids a race with it.
  delayedSelection: true,

  renderPicker: (mode, pickerProps) => {
    if (mode === 'year') return React.createElement(YearPicker, { ...pickerProps })
    if (mode === 'month') return React.createElement(MonthPicker, { ...pickerProps, hasHeader: true })
    if (mode === 'day') return React.createElement(DayPicker, { ...pickerProps })
    if (mode === 'hour') return React.createElement(HourPicker, { ...pickerProps, hasHeader: true })

    return React.createElement(MinutePicker, { ...pickerProps, hasHeader: true })
  },
}

/**
 * Component responsibility:
 *  - parse input value
 *  - handle underlying picker change
 */
function DateTimeInput(props: DateTimeInputProps): React.ReactElement {
  const {
    value,
    dateTimeFormat,
    dateFormat,
    onChange,
    closable,
    localization,
    timeFormat,
    initialDate,
    disable,
    maxDate,
    minDate,
    preserveViewMode,
    startMode,
    divider,
    markColor,
    marked,
    disableMinute,
    inline,
    ...rest
  } = props as DateTimeInputProps & Record<DroppedDateTimeInputProp, unknown>

  const input = useDateInput(props, DATE_TIME_INPUT_CONFIG)

  return React.createElement(InputView, {
    popupIsClosed: input.popupIsClosed,
    closePopup: input.closePopup,
    openPopup: input.openPopup,
    onMount: input.onInputViewMount,
    onChange: input.handleInputValueChange,
    ...rest,
    value: dateValueToString(value, dateFormat, localization!),
    renderPicker: input.renderPicker,
  })
}

// `icon` is a legacy default kept for the runtime statics contract: `InputView` renders its own
// calendar icon and drops the prop.
DateTimeInput.defaultProps = {
  ...BaseInputDefaultProps,
  dateFormat: 'DD-MM-YYYY',
  icon: 'calendar',
} satisfies Partial<DateTimeInputProps & { icon?: string }>



export default DateTimeInput
