const API_BASE_URL =
    import.meta.env.VITE_API_BASE_URL || "/api";

let reissuePromise = null;

class ApiError extends Error {
    constructor(status, code, message, data) {
        super(message);

        this.name = "ApiError";
        this.status = status;
        this.code = code;
        this.data = data;
    }
}

const sendRequest = async (
    path,
    options = {}
) => {
    const headers = new Headers(options.headers);

    if (!headers.has("Accept")) {
        headers.set("Accept", "application/json");
    }

    return fetch(`${API_BASE_URL}${path}`, {
        ...options,
        headers,
        credentials: "include",
    });
};

const reissueAccessToken = async () => {
    if (reissuePromise !== null) {
        return reissuePromise;
    }

    reissuePromise = fetch(
        `${API_BASE_URL}/auth/reissue`,
        {
            method: "POST",
            credentials: "include",
            headers: {
                Accept: "application/json",
            },
        }
    )
        .then((response) => {
            return response.ok;
        })
        .catch(() => {
            return false;
        })
        .finally(() => {
            reissuePromise = null;
        });

    return reissuePromise;
};

const parseResponse = async (response) => {
    const contentType =
        response.headers.get("content-type");

    let result = null;

    if (
        contentType &&
        contentType.includes("application/json")
    ) {
        result = await response.json();
    }

    if (!response.ok) {
        throw new ApiError(
            response.status,
            result?.code || "UNKNOWN_ERROR",
            result?.message || "요청 처리에 실패했습니다.",
            result?.data || null
        );
    }

    return result;
};

const apiRequest = async (
    path,
    options = {}
) => {
    let response = await sendRequest(
        path,
        options
    );

    const isReissueRequest =
        path === "/auth/reissue";

    if (
        response.status === 401 &&
        !isReissueRequest
    ) {
        const reissueSucceeded =
            await reissueAccessToken();

        if (reissueSucceeded) {
            response = await sendRequest(
                path,
                options
            );
        }
    }

    return parseResponse(response);
};

const parseBlobResponse = async (response) => {
    if (!response.ok) {
        let result = null;
        const contentType =
            response.headers.get("content-type");

        if (
            contentType &&
            contentType.includes("application/json")
        ) {
            result = await response.json();
        }

        throw new ApiError(
            response.status,
            result?.code || "UNKNOWN_ERROR",
            result?.message || "요청 처리에 실패했습니다.",
            result?.data || null
        );
    }

    return response.blob();
};

const apiBlobRequest = async (
    path,
    options = {}
) => {
    let response = await sendRequest(
        path,
        options
    );

    if (response.status === 401) {
        const reissueSucceeded =
            await reissueAccessToken();

        if (reissueSucceeded) {
            response = await sendRequest(
                path,
                options
            );
        }
    }

    return parseBlobResponse(response);
};

export {
    API_BASE_URL,
    ApiError,
    apiBlobRequest,
    apiRequest,
};
