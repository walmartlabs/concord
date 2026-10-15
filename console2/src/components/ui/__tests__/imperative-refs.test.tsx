import React, { createRef } from 'react'
import { cleanup, render } from '@testing-library/react'
import { afterEach, describe, expect, test } from 'vitest'
import { Button, Input, TextArea } from '@/components/ui'
import type { ButtonHandle, InputHandle, TextAreaHandle } from '@/components/ui'

afterEach(() => cleanup())

describe('imperative component refs', () => {
  test('Input focuses and selects its native input and composes a child ref', () => {
    const inputRef = createRef<InputHandle>()
    const childRef = createRef<HTMLInputElement>()
    const { getByRole } = render(
      <Input ref={inputRef}>
        <input ref={childRef} defaultValue="select this text" />
      </Input>,
    )
    const input = getByRole('textbox') as HTMLInputElement

    expect(childRef.current).toBe(input)
    inputRef.current!.focus()
    expect(document.activeElement).toBe(input)

    input.setSelectionRange(0, 0)
    inputRef.current!.select()
    expect(input.selectionStart).toBe(0)
    expect(input.selectionEnd).toBe(input.value.length)
  })

  test('Button and TextArea focus their native controls', () => {
    const buttonRef = createRef<ButtonHandle>()
    const textareaRef = createRef<TextAreaHandle>()
    const { getByRole } = render(
      <>
        <Button ref={buttonRef}>Action</Button>
        <TextArea ref={textareaRef} />
      </>,
    )
    const button = getByRole('button', { name: 'Action' })
    const textarea = getByRole('textbox')

    buttonRef.current!.focus()
    expect(document.activeElement).toBe(button)

    textareaRef.current!.focus()
    expect(document.activeElement).toBe(textarea)
  })

})
