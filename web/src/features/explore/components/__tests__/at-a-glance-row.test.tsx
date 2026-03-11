import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { AtAGlanceRow } from "@/features/explore/components/at-a-glance-row";

describe("AtAGlanceRow", () => {
  it("renders the section with test id", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        platformCount={3}
        avgRating={85}
      />,
    );
    expect(screen.getByTestId("at-a-glance-row")).toBeInTheDocument();
  });

  it("renders total games pill", () => {
    render(
      <AtAGlanceRow
        gameCount={42}
        platformCount={2}
        avgRating={0}
      />,
    );
    const pill = screen.getByTestId("glance-total-games");
    expect(pill).toHaveTextContent("42");
    expect(pill).toHaveTextContent("Total games");
  });

  it("renders active years as range", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        activeYears={{ first: 1986, last: 2004 }}
        platformCount={2}
        avgRating={0}
      />,
    );
    const pill = screen.getByTestId("glance-active-years");
    expect(pill).toHaveTextContent("1986");
    expect(pill).toHaveTextContent("2004");
    expect(pill).toHaveTextContent("Active years");
  });

  it("renders active years as single year when first equals last", () => {
    render(
      <AtAGlanceRow
        gameCount={5}
        activeYears={{ first: 1999, last: 1999 }}
        platformCount={1}
        avgRating={0}
      />,
    );
    const pill = screen.getByTestId("glance-active-years");
    expect(pill).toHaveTextContent("1999");
  });

  it("hides active years pill when not provided", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        platformCount={2}
        avgRating={80}
      />,
    );
    expect(screen.queryByTestId("glance-active-years")).not.toBeInTheDocument();
  });

  it("renders primary genre pill", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        primaryGenre="Platformer"
        platformCount={2}
        avgRating={0}
      />,
    );
    const pill = screen.getByTestId("glance-primary-genre");
    expect(pill).toHaveTextContent("Platformer");
    expect(pill).toHaveTextContent("Primary genre");
  });

  it("hides primary genre pill when not provided", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        platformCount={2}
        avgRating={80}
      />,
    );
    expect(
      screen.queryByTestId("glance-primary-genre"),
    ).not.toBeInTheDocument();
  });

  it("renders platform count pill", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        platformCount={5}
        avgRating={0}
      />,
    );
    const pill = screen.getByTestId("glance-platforms");
    expect(pill).toHaveTextContent("5");
    expect(pill).toHaveTextContent("Platforms");
  });

  it("renders singular platform label for 1 platform", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        platformCount={1}
        avgRating={0}
      />,
    );
    const pill = screen.getByTestId("glance-platforms");
    expect(pill).toHaveTextContent("1");
    expect(pill).toHaveTextContent("Platform");
  });

  it("hides platform pill when count is 0", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        platformCount={0}
        avgRating={80}
      />,
    );
    expect(screen.queryByTestId("glance-platforms")).not.toBeInTheDocument();
  });

  it("renders avg rating pill when > 0", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        platformCount={2}
        avgRating={88.5}
      />,
    );
    const pill = screen.getByTestId("glance-avg-rating");
    expect(pill).toHaveTextContent("88.5");
    expect(pill).toHaveTextContent("Avg rating");
  });

  it("hides avg rating pill when 0", () => {
    render(
      <AtAGlanceRow
        gameCount={10}
        platformCount={2}
        avgRating={0}
      />,
    );
    expect(screen.queryByTestId("glance-avg-rating")).not.toBeInTheDocument();
  });

  it("renders all pills when all data is provided", () => {
    render(
      <AtAGlanceRow
        gameCount={50}
        activeYears={{ first: 1985, last: 2010 }}
        primaryGenre="Action"
        platformCount={4}
        avgRating={91.2}
      />,
    );
    expect(screen.getByTestId("glance-total-games")).toBeInTheDocument();
    expect(screen.getByTestId("glance-active-years")).toBeInTheDocument();
    expect(screen.getByTestId("glance-primary-genre")).toBeInTheDocument();
    expect(screen.getByTestId("glance-platforms")).toBeInTheDocument();
    expect(screen.getByTestId("glance-avg-rating")).toBeInTheDocument();
  });
});
