export const THEME_OPTIONS: Array<{
  value: string;
  label: string;
  description: string;
  colorScheme: "light" | "dark";
  swatches: string[];
}> = [
  {
    value: "default-dark",
    label: "Default Dark",
    description: "Current dark theme",
    colorScheme: "dark",
    swatches: ["#16191d", "#212529", "#00bfff", "#343a40"],
  },
  {
    value: "default-light",
    label: "Default Light",
    description: "Clean light theme",
    colorScheme: "light",
    swatches: ["#f8f9fa", "#ffffff", "#0081b3", "#e9ecef"],
  },
  {
    value: "nintendo-colorful",
    label: "Nintendo Colorful",
    description: "Bright, joyful, retro",
    colorScheme: "light",
    swatches: ["#faf8f4", "#E52521", "#43B047", "#F5C722"],
  },
  {
    value: "ocean-dark",
    label: "Ocean Dark",
    description: "Deep blue dark theme",
    colorScheme: "dark",
    swatches: ["#04111f", "#0d2a3d", "#00c896", "#153a50"],
  },
  {
    value: "sunset-warm",
    label: "Sunset Warm",
    description: "Warm amber light theme",
    colorScheme: "light",
    swatches: ["#fdf8f4", "#e8622a", "#d4902e", "#eeddd0"],
  },
];
