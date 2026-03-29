let transitionPlayer = null;

export const registerPageTransitionPlayer = (player) => {
  transitionPlayer = player;
};

export const playPageTransition = (options) => {
  return transitionPlayer?.play?.(options) ?? Promise.resolve();
};
