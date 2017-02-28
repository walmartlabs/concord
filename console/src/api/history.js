// @flow
import {apiListQuery} from "./common";

export const fetchRows = apiListQuery("fetchHistoryRows", "/api/v1/history");

