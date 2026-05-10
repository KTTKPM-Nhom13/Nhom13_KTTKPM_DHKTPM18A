import axios from 'axios';
import { env } from '../config/env';

const authHttp = axios.create({
  baseURL: env.apiGatewayUrl,
  timeout: 10000,
});

export default authHttp;
