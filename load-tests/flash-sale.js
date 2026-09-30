import http from 'k6/http';
import {check} from 'k6';
import {Counter, Rate} from 'k6/metrics';

export const options = {
    scenarios: {
        flash_sale: {executor: 'constant-vus', vus: Number(__ENV.VUS || 100), duration: __ENV.DURATION || '10s'}
    },
    thresholds: {unexpected_errors: ['rate==0'], http_req_duration: ['p(95)<2000']}
};

const businessConflicts = new Counter('capacity_conflicts');
const successfulReservations = new Counter('successful_reservations');
const unexpectedErrors = new Rate('unexpected_errors');
const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const response = http.post(`${baseUrl}/eventos/${__ENV.EVENT_ID}/reservas`,
        JSON.stringify({quantity: Number(__ENV.QUANTITY || 1)}), {
            headers: {'Content-Type': 'application/json', 'Idempotency-Key': `k6-${__VU}-${__ITER}`}
        });
    const success = response.status === 201;
    const capacityConflict = response.status === 422;
    successfulReservations.add(success);
    businessConflicts.add(capacityConflict);
    unexpectedErrors.add(!success && !capacityConflict);
    check(response, {'reservation or expected sellout': () => success || capacityConflict});
}
