/**
 * Compile-only contract fixture (not a runtime Vitest test).
 *
 * These assertions catch accidental `any` exports and loss of required props
 * during the `.js` + `.d.ts` → `.tsx` declaration merge. It is covered by the
 * project's `tsc` include; nothing here is executed.
 */
import React from 'react'
import {
  Button,
  Card,
  Dropdown,
  Form,
  Icon,
  Input,
  Table,
  TextArea,
} from '@/components/ui'
import type { ButtonHandle, InputHandle, InputOnChangeData, TextAreaHandle } from '@/components/ui'
import Select from '@/components/ui/addons/Select'
import type { DateInputProps, DateTimeInputProps } from '@/components/ui/calendar'
import ButtonGroup from '@/components/ui/elements/Button/ButtonGroup'
import FormInput from '@/components/ui/collections/Form/FormInput'
import DropdownMenu from '@/components/ui/modules/Dropdown/DropdownMenu'
import TableRow from '@/components/ui/collections/Table/TableRow'
import CardHeader from '@/components/ui/views/Card/CardHeader'

// Valid compound-static JSX and direct subcomponent imports must compile.
export const validCompoundStatics = (
  <>
    <Button.Group>Group</Button.Group>
    <Form.Input />
    <Dropdown.Menu />
    <Table.Row />
    <Card.Header />
    <ButtonGroup />
    <FormInput />
    <DropdownMenu />
    <TableRow />
    <CardHeader />
  </>
)

// Public component refs expose their typed imperative instance methods.
const inputRef = React.createRef<InputHandle>()
const buttonRef = React.createRef<ButtonHandle>()
const textAreaRef = React.createRef<TextAreaHandle>()
export const validImperativeRefs = (
  <>
    <Input ref={inputRef} />
    <Button ref={buttonRef} />
    <TextArea ref={textAreaRef} />
  </>
)

// InputOnChangeData.value is a string.
export function readInputValue(data: InputOnChangeData): string {
  return data.value
}

// Date/date-time callbacks keep string payloads.
const onDateChange: DateInputProps['onChange'] = (_e, data) => {
  const value: string = data.value
  void value
}
const onDateTimeChange: DateTimeInputProps['onChange'] = (_e, data) => {
  const value: string = data.value
  void value
}
void onDateChange
void onDateTimeChange

// Invalid: InputOnChangeData.value is a string, so toFixed() must not type-check.
// @ts-expect-error — InputOnChangeData.value is a string
export const invalidInputValue = (data: InputOnChangeData): number => data.value.toFixed()

// Invalid: Dropdown value must be scalar/array, not an object.
// @ts-expect-error — object-valued Dropdown value is invalid
export const invalidDropdownValue = <Dropdown value={{ a: 1 }} />

// Invalid: Select requires options.
// @ts-expect-error — options is required on Select
export const missingSelectOptions = <Select />

// Invalid: Icon name must be a member of SemanticICONS.
// @ts-expect-error — not a valid icon name
export const invalidIconName = <Icon name="not-a-real-icon-name" />

// .create shorthand keeps typed options and rejects bad override keys.
export const validButtonCreate = Button.create('Click', {
  defaultProps: { primary: true },
  overrideProps: { color: 'red' },
})
export const invalidButtonOverride = Button.create('Click', {
  // @ts-expect-error — override key is not a ButtonProps prop
  overrideProps: { notAButtonProp: 123 },
})
