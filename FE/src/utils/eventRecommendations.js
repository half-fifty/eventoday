export const distanceInKilometers = (origin, destination) => {
  if (!hasEventCoordinates(origin) || !hasEventCoordinates(destination)) return Number.POSITIVE_INFINITY;
  const toRadians = (degree) => (degree * Math.PI) / 180;
  const latitudeDelta = toRadians(Number(destination.latitude) - Number(origin.latitude));
  const longitudeDelta = toRadians(Number(destination.longitude) - Number(origin.longitude));
  const originLatitude = toRadians(Number(origin.latitude));
  const destinationLatitude = toRadians(Number(destination.latitude));
  const haversine = Math.sin(latitudeDelta / 2) ** 2 + Math.cos(originLatitude) * Math.cos(destinationLatitude) * Math.sin(longitudeDelta / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
};

export const hasEventCoordinates = (event) =>
  event?.latitude !== null && event?.latitude !== undefined && event?.latitude !== "" &&
  event?.longitude !== null && event?.longitude !== undefined && event?.longitude !== "" &&
  Number.isFinite(Number(event.latitude)) && Number.isFinite(Number(event.longitude));

export const rankNearbyEvents = (origin, candidates = [], limit = 4) => candidates
  .filter((candidate) => Number(candidate.id) !== Number(origin?.id))
  .map((candidate) => ({ ...candidate, distance: distanceInKilometers(origin, candidate) }))
  .sort((a, b) => a.distance - b.distance || new Date(a.startAt) - new Date(b.startAt))
  .slice(0, limit);

export const formatEventDistance = (distance) => {
  if (!Number.isFinite(distance)) return "거리 정보 없음";
  if (distance < 1) return `약 ${Math.max(1, Math.round(distance * 1000))}m`;
  return `약 ${distance.toFixed(distance < 10 ? 1 : 0)}km`;
};
