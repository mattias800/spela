import { Monitor, Smartphone, Laptop } from "lucide-react";

export function getPlatformIcon(platform: string) {
  switch (platform.toLowerCase()) {
    case "android":
      return Smartphone;
    case "macos":
    case "linux":
    case "windows":
      return Laptop;
    default:
      return Monitor;
  }
}
