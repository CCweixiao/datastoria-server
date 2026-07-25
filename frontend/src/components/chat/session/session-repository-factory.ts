import { RemoteSessionRepository } from "./remote-session-repository";
import type { SessionRepository } from "./session-repository";

const remoteSessionRepository = new RemoteSessionRepository();

export function getSessionRepository(): SessionRepository {
  return remoteSessionRepository;
}
