import { combineReducers } from "redux"

import { reducers as secretCreate } from "./create"
import { reducers as secretList } from "./list"

let secret = combineReducers({ secretCreate, secretList })

export default secret