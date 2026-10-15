
import React from 'react'
import DayPicker from '../pickers/dayPicker/DayPicker'
import MonthPicker from '../pickers/monthPicker/MonthPicker'
import YearPicker from '../pickers/YearPicker'
import InputView from '../views/InputView'
import {
  BaseInputDefaultProps,
  BaseInputProps,
  DateInputConfig,
  useDateInput,
} from './BaseInput'
import { dateValueToString } from './parse'

type CalendarMode = 'year' | 'month' | 'day'

export interface DateInputProps extends BaseInputProps {
  dateFormat?: string
}

export type DateInputOnChangeData = DateInputProps

function getNextMode(currentMode: CalendarMode): CalendarMode {
  if (currentMode === 'year') {
    return 'month'
  }

  if (currentMode === 'month') {
    return 'day'
  }

  return 'year'
}

function getPrevMode(currentMode: CalendarMode): CalendarMode {
  if (currentMode === 'day') {
    return 'month'
  }

  if (currentMode === 'month') {
    return 'year'
  }

  return 'day'
}

/** Legacy calendar props accepted from untyped callers; they never reach the input. */
type DroppedDateInputProp =
  | 'disable'
  | 'enable'
  | 'initialDate'
  | 'inline'
  | 'markColor'
  | 'marked'
  | 'maxDate'
  | 'minDate'
  | 'preserveViewMode'
  | 'startMode'

const DATE_INPUT_CONFIG: DateInputConfig<CalendarMode> = {
  getFormat: (props) => props.dateFormat,

  getDateValues: (value) => ({ year: value.year(), month: value.month(), date: value.date() }),

  getNextMode,
  getPrevMode,
  commitMode: 'day',

  renderPicker: (mode, pickerProps) => {
    if (mode === 'year') {
      return React.createElement(YearPicker, { ...pickerProps })
    }

    if (mode === 'month') {
      return React.createElement(MonthPicker, { ...pickerProps, hasHeader: true })
    }

    return React.createElement(DayPicker, { ...pickerProps })
  },
}

/**
 * Component responsibility:
 *  - parse input value
 *  - handle underlying picker change
 */
function DateInput(props: DateInputProps): React.ReactElement {
  const {
    value,
    dateFormat,
    onChange,
    closable,
    localization,
    initialDate,
    disable,
    enable,
    maxDate,
    minDate,
    preserveViewMode,
    startMode,
    markColor,
    marked,
    inline,
    ...rest
  } = props as DateInputProps & Record<DroppedDateInputProp, unknown>

  const input = useDateInput(props, DATE_INPUT_CONFIG)

  return React.createElement(InputView, {
    closePopup: input.closePopup,
    openPopup: input.openPopup,
    popupIsClosed: input.popupIsClosed,
    onMount: input.onInputViewMount,
    onChange: input.handleInputValueChange,
    ...rest,
    renderPicker: input.renderPicker,
    value: dateValueToString(value, dateFormat, localization!),
  })
}

// `icon` is a legacy default kept for the runtime statics contract: `InputView` renders its own
// calendar icon and drops the prop.
DateInput.defaultProps = {
  ...BaseInputDefaultProps,
  dateFormat: 'DD-MM-YYYY',
  icon: 'calendar',
} satisfies Partial<DateInputProps & { icon?: string }>



export default DateInput
