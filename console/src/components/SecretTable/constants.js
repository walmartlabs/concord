// @flow
import {sort} from "../../constants";

export const columns = [
    {key: "name", label: "Name", collapsing: true},
//    {key: "templates", label: "Templates"},
    {key: "actions", label: "Actions", collapsing: true}
];

export const nameKey = "name";
//export const templatesKey = "templates";
export const sortableKeys = ["name"];
export const actionsKey = "actions";

export const defaultSortKey = "name";
export const defaultSortDir = sort.ASC;
