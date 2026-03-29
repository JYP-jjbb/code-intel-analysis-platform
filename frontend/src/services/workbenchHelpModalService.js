let opener = null;

export const registerWorkbenchHelpOpener = (openHandler) => {
  opener = typeof openHandler === "function" ? openHandler : null;
  return () => {
    if (opener === openHandler) {
      opener = null;
    }
  };
};

export const openWorkbenchHelpModal = () => {
  if (typeof opener === "function") {
    opener();
  }
};
