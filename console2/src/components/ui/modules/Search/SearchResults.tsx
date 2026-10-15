import cx from 'clsx'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface SearchResultsProps
  extends StrictSearchResultsProps,
    React.HTMLAttributes<HTMLDivElement> {}

export interface StrictSearchResultsProps {
  as?: React.ElementType
  children?: React.ReactNode
  className?: string
}

function SearchResults(props: SearchResultsProps): React.ReactElement {
  const { children, className } = props

  const classes = cx('results transition', className)
  const rest = getUnhandledProps(SearchResults, props)
  const ElementType = getElementType(SearchResults, props)

  return React.createElement(ElementType, { ...rest, className: classes }, children)
}

SearchResults.handledProps = ['as', 'children', 'className']



export default SearchResults
