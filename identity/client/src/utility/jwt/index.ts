export const decode = (token: string) => {
  const base64Url = token.split(".")[1];
  const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
  const jsonPayload = decodeURIComponent(
    window
      .atob(base64)
      .split("")
      .map(function (c) {
        return "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2);
      })
      .join(""),
  );

  return JSON.parse(jsonPayload);
};

export const getExpiration = (token: string): number => {
  const decoded = decode(token);
  return decoded.exp;
};

export const isExpired = (token: string): boolean => {
  try {
    const expiration = getExpiration(token);
    const now = new Date().getTime() / 1000;
    return expiration < now;
  } catch (e) {
    return true;
  }
};
