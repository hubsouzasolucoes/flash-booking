import http from 'k6/http';
import {check} from 'k6';

export const options = {vus: 100, duration: '10s'};
const eventId = __ENV.EVENT_ID;
export default function () {
    const key = `${__VU}-${__ITER}`;
    const r = http.post(`http://localhost:8080/events/${eventId}/reservations`, JSON.stringify({quantity: 1}), {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': key
        }
    });
    check(r, {'201 or 409': x => x.status === 201 || x.status === 409});
}
