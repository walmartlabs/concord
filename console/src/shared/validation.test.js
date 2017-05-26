import * as v from "./validation";

it("handles invalid repository URLs", () => {
    expect(v.repository.url("ftp://olala/")).toBeDefined();
    expect(v.repository.url("ssh://git@github.com:takari/bpm.git")).toBeDefined();
});

it("handles valid repository URLs", () => {
    expect(v.repository.url("git@github.com:takari/bpm.git")).toBeUndefined();
});
