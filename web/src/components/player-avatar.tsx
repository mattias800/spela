const sizeClasses = {
  sm: "h-8 w-8 text-xs",
  md: "h-10 w-10 text-sm",
  lg: "h-20 w-20 text-2xl",
} as const;

interface PlayerAvatarProps {
  username: string;
  avatarUrl?: string;
  size?: "sm" | "md" | "lg";
}

export function PlayerAvatar({ username, avatarUrl, size = "sm" }: PlayerAvatarProps) {
  const classes = sizeClasses[size];

  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={username}
        className={`${classes} rounded-full object-cover`}
      />
    );
  }

  return (
    <div className={`${classes} rounded-full bg-gradient-to-br from-brand-500 to-brand-700 flex items-center justify-center font-bold text-white`}>
      {username.charAt(0).toUpperCase()}
    </div>
  );
}
